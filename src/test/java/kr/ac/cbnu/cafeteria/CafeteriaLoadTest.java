package kr.ac.cbnu.cafeteria;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "CAFETERIA_LOAD_TEST", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:cafeteria-load;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER"
})
class CafeteriaLoadTest {

    private static final int USERS = 20;
    private static final int MENUS = 20;
    private static final int RESERVATIONS = 1000;
    private static final int REQUESTS_PER_USER = 5;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @DynamicPropertySource
    static void useMySqlWhenRequested(DynamicPropertyRegistry registry) {
        TestDatabase.configureMySql(registry, "cafeteria_load_test");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void twentyStudentsReadMenusAndReservations() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menus", Integer.class))
                .as("부하 시험 전용 DB의 메뉴 테이블은 비어 있어야 합니다").isZero();

        try {
            seedData();
            List<String> sessions = new ArrayList<>();
            for (int i = 0; i < USERS; i++) {
                sessions.add(login(studentId(i)));
            }
            // 메뉴와 예약 조회를 각 한 번 미리 실행하고 측정에서 제외한다.
            read("/api/menus/today", sessions.get(0));
            read("/api/reservations/me", sessions.get(0));

            List<Long> nanos = measure(sessions);
            long p95 = nanos.stream().sorted().toList().get((int) Math.ceil(nanos.size() * 0.95) - 1);
            String result = String.format(Locale.ROOT, "NFR-01: %d requests, %d concurrent users, p95 %.1f ms",
                    nanos.size(), USERS, p95 / 1_000_000.0);
            System.out.println(result);
            assertThat(nanos).hasSize(USERS * REQUESTS_PER_USER);
            assertThat(p95).as(result).isLessThanOrEqualTo(Duration.ofSeconds(2).toNanos());
        } finally {
            removeFixtures();
        }
    }

    private List<Long> measure(List<String> sessions) throws Exception {
        var workers = Executors.newFixedThreadPool(USERS);
        var ready = new CountDownLatch(USERS);
        var start = new CountDownLatch(1);
        try {
            List<Future<List<Long>>> jobs = new ArrayList<>();
            for (int user = 0; user < USERS; user++) {
                String session = sessions.get(user);
                int firstEndpoint = user % 2;
                jobs.add(workers.submit(() -> {
                    ready.countDown();
                    start.await();
                    List<Long> durations = new ArrayList<>();
                    for (int i = 0; i < REQUESTS_PER_USER; i++) {
                        String path = (firstEndpoint + i) % 2 == 0 ? "/api/menus/today" : "/api/reservations/me";
                        long began = System.nanoTime();
                        read(path, session);
                        durations.add(System.nanoTime() - began);
                    }
                    return durations;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Long> result = new ArrayList<>();
            for (Future<List<Long>> job : jobs) {
                result.addAll(job.get(60, TimeUnit.SECONDS));
            }
            return result;
        } finally {
            workers.shutdownNow();
        }
    }

    private void seedData() {
        String hash = encoder.encode("load-test-only");
        List<Object[]> students = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            students.add(new Object[]{studentId(i), hash});
        }
        jdbc.batchUpdate("INSERT INTO users (user_id, password_hash, name, role) VALUES (?, ?, 'Load student', 'STUDENT')",
                students);

        LocalDate today = LocalDate.now(clock);
        List<Object[]> menus = new ArrayList<>();
        for (int i = 0; i < MENUS; i++) {
            menus.add(new Object[]{"Load menu " + i, today, i < MENUS / 2 ? "LUNCH" : "DINNER"});
        }
        jdbc.batchUpdate("""
                INSERT INTO menus (name, price, sale_date, meal_type, total_quantity, remaining_quantity, admin_id)
                VALUES (?, 5000, ?, ?, 100, 50, 'admin')
                """, menus);
        List<Integer> menuIds = jdbc.queryForList("SELECT menu_id FROM menus ORDER BY menu_id", Integer.class);
        List<Integer> slotIds = jdbc.queryForList("SELECT slot_id FROM time_slots ORDER BY slot_id", Integer.class);
        jdbc.update("UPDATE time_slots SET capacity = 200");

        List<Object[]> reservations = new ArrayList<>();
        for (int i = 0; i < RESERVATIONS; i++) {
            int menu = i % MENUS;
            int mealSlots = menu < MENUS / 2 ? 0 : 3;
            int slot = mealSlots + i / MENUS % 3;
            reservations.add(new Object[]{studentId((menu + i / MENUS) % USERS), menuIds.get(menu), slotIds.get(slot),
                    LocalDateTime.of(today, java.time.LocalTime.of(9, 0)).plusSeconds(i)});
        }
        jdbc.batchUpdate("""
                INSERT INTO reservations (student_id, menu_id, slot_id, quantity, total_price, status, created_at)
                VALUES (?, ?, ?, 1, 5000, 'RESERVED', ?)
                """, reservations);
        jdbc.update("""
                INSERT INTO payments (reservation_id, amount, method, approval_no, status, paid_at)
                SELECT reservation_id, total_price, 'CARD', CONCAT('MOCK-', reservation_id), 'PAID', created_at FROM reservations
                """);
        jdbc.update("""
                INSERT INTO tickets (reservation_id, qr_code, issued_at, status)
                SELECT reservation_id, CONCAT('load-', reservation_id), created_at, 'VALID' FROM reservations
                """);
    }

    private void removeFixtures() {
        jdbc.update("DELETE FROM tickets");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("DELETE FROM menus");
        jdbc.update("DELETE FROM users WHERE role = 'STUDENT' AND user_id LIKE 'load-%'");
        jdbc.update("UPDATE time_slots SET capacity = 30");
    }

    private String login(String studentId) throws Exception {
        HttpResponse<String> page = send(request("/login.html").GET().build());
        String csrf = cookie(page, "XSRF-TOKEN").getValue();
        String body = "{\"id\":\"" + studentId + "\",\"pw\":\"load-test-only\"}";
        HttpResponse<String> login = send(request("/api/auth/login")
                .header("Cookie", "XSRF-TOKEN=" + csrf)
                .header("X-XSRF-TOKEN", csrf)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        return "JSESSIONID=" + cookie(login, "JSESSIONID").getValue();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(REQUEST_TIMEOUT);
    }

    private void read(String path, String session) throws Exception {
        send(request(path).header("Cookie", session).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(request.uri().getPath()).isEqualTo(200);
        return response;
    }

    private static HttpCookie cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .flatMap(header -> HttpCookie.parse(header).stream())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
    }

    private static String studentId(int index) {
        return "load-" + index;
    }
}
