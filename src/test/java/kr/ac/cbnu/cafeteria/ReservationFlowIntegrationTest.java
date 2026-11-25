package kr.ac.cbnu.cafeteria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.cbnu.cafeteria.domain.Admin;
import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import kr.ac.cbnu.cafeteria.repository.AdminRepository;
import kr.ac.cbnu.cafeteria.repository.MenuRepository;
import kr.ac.cbnu.cafeteria.repository.PaymentRepository;
import kr.ac.cbnu.cafeteria.repository.ReservationRepository;
import kr.ac.cbnu.cafeteria.repository.TicketRepository;
import kr.ac.cbnu.cafeteria.repository.TimeSlotRepository;
import kr.ac.cbnu.cafeteria.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 통합 테스트(블랙박스·통합 테스트). 서버 시각을 2026-11-26 10:00(서울)으로 고정한다.
 * 초기 데이터: 관리자 admin, 학생 20240001·20240002, 중식 시간대 11:30·12:00·12:30(정원 30).
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationFlowIntegrationTest {

    static final LocalDate DAY = LocalDate.of(2026, 11, 26);
    static final MutableClock CLOCK = new MutableClock(DAY.atTime(10, 0));

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return CLOCK;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminRepository admins;
    @Autowired MenuRepository menus;
    @Autowired TimeSlotRepository timeSlots;
    @Autowired ReservationRepository reservations;
    @Autowired PaymentRepository payments;
    @Autowired TicketRepository tickets;
    @Autowired ReservationService reservationService;

    Integer lunchMenuId;
    Integer noonSlotId;

    @BeforeEach
    void setUp() {
        CLOCK.set(DAY.atTime(10, 0));
        tickets.deleteAll();
        payments.deleteAll();
        reservations.deleteAll();
        menus.deleteAll();
        lunchMenuId = createMenu("제육덮밥", 5500, MealType.LUNCH, 10).getMenuId();
        noonSlotId = timeSlots.findByMealTypeOrderByStartTimeAsc(MealType.LUNCH).stream()
                .filter(s -> s.getStartTime().equals(LocalTime.of(12, 0))).findFirst().orElseThrow().getSlotId();
    }

    // ---------------------------------------------------------------- helpers

    Menu createMenu(String name, int price, MealType meal, int qty) {
        Admin admin = admins.findById("admin").orElseThrow();
        return menus.save(new Menu(name, price, DAY, meal, qty, admin));
    }

    static RequestPostProcessor student(String id) {
        return user(id).roles("STUDENT");
    }

    static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    MockHttpServletRequestBuilder postJson(String url, Object body) throws Exception {
        return post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    int reserve(String studentId, Integer menuId, Integer slotId, int qty) throws Exception {
        String body = mvc.perform(postJson("/api/reservations", Map.of("menuId", menuId, "slotId", slotId, "quantity", qty))
                        .with(student(studentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("reservationId").asInt();
    }

    void pay(String studentId, int reservationId) throws Exception {
        mvc.perform(postJson("/api/reservations/" + reservationId + "/payment", Map.of("method", "CARD"))
                        .with(student(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    int remaining(Integer menuId) {
        return menus.findById(menuId).orElseThrow().getRemainingQuantity();
    }

    String qrOf(int reservationId) {
        return tickets.findByReservationId(reservationId).orElseThrow().getQrCode();
    }

    // ---------------------------------------------------------------- tests

    @Test
    @DisplayName("로그인: 실패는 같은 메시지로 401, 성공하면 세션으로 역할을 확인한다(FR-01)")
    void login() throws Exception {
        mvc.perform(postJson("/api/auth/login", Map.of("id", "20240001", "pw", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));
        mvc.perform(postJson("/api/auth/login", Map.of("id", "nobody", "pw", "student1234")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다"));

        MockHttpSession session = (MockHttpSession) mvc.perform(
                        postJson("/api/auth/login", Map.of("id", "20240001", "pw", "student1234")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn().getRequest().getSession();
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김학생"));
        mvc.perform(get("/api/menus/today").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/menus/today").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("접근 통제: 미로그인 401, 학생의 관리자 API 403, 다른 학생 예약 403(NFR-02)")
    void accessControl() throws Exception {
        mvc.perform(get("/api/menus/today")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/admin/menus").with(student("20240001"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/menus/today").with(admin())).andExpect(status().isForbidden());

        int id = reserve("20240001", lunchMenuId, noonSlotId, 1);
        mvc.perform(get("/api/reservations/" + id).with(student("20240002"))).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/reservations/" + id + "/payment", Map.of("method", "CARD")).with(student("20240002")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF 토큰 없는 상태 변경 요청은 거부된다(EIR-NW-04)")
    void csrfRequired() throws Exception {
        mvc.perform(post("/api/reservations").with(student("20240001")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":1,\"slotId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("예약 → 결제 → 식권 → 수령: 재고 차감, 식권 1회 사용, 현황 집계(UC-04~06, UC-13~14)")
    void fullFlow() throws Exception {
        int id = reserve("20240001", lunchMenuId, noonSlotId, 2);
        assertThat(remaining(lunchMenuId)).isEqualTo(10);              // 결제대기는 재고를 차지하지 않는다

        mvc.perform(get("/api/reservations/me").with(student("20240001")))
                .andExpect(jsonPath("$.length()").value(0));          // 결제대기는 목록에서 제외(FR-08)

        pay("20240001", id);
        assertThat(remaining(lunchMenuId)).isEqualTo(8);

        mvc.perform(get("/api/reservations/me").with(student("20240001")))
                .andExpect(jsonPath("$[0].status").value("RESERVED"))
                .andExpect(jsonPath("$[0].totalPrice").value(11000))
                .andExpect(jsonPath("$[0].cancellable").value(true));
        mvc.perform(get("/api/reservations/" + id + "/ticket/qr").with(student("20240001")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        mvc.perform(get("/api/admin/reservations/status").param("date", DAY.toString()).with(admin()))
                .andExpect(jsonPath("$[?(@.slotId == " + noonSlotId + ")].reservedCount").value(1));

        // 예약 번호로 조회(상태 변경 없음) → QR로 수령 → 같은 식권 재사용 거절
        mvc.perform(get("/api/admin/tickets/lookup").param("code", String.valueOf(id)).with(admin()))
                .andExpect(jsonPath("$.ticketStatus").value("VALID"))
                .andExpect(jsonPath("$.studentName").value("김학생"));
        mvc.perform(postJson("/api/admin/pickups", Map.of("code", qrOf(id))).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("USED"))
                .andExpect(jsonPath("$.reservationStatus").value("PICKED_UP"));
        mvc.perform(postJson("/api/admin/pickups", Map.of("code", String.valueOf(id))).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 수령된 식권입니다"));

        mvc.perform(get("/api/admin/reservations/status").param("date", DAY.toString()).with(admin()))
                .andExpect(jsonPath("$[?(@.slotId == " + noonSlotId + ")].pickedUpCount").value(1));
        mvc.perform(postJson("/api/reservations/" + id + "/cancel", Map.of()).with(student("20240001")))
                .andExpect(status().isConflict());                    // 수령완료는 취소 불가
    }

    @Test
    @DisplayName("같은 결제 요청을 다시 보내도 결제·재고는 1회만 반영된다(NFR-05)")
    void paymentIsIdempotent() throws Exception {
        int id = reserve("20240001", lunchMenuId, noonSlotId, 1);
        pay("20240001", id);
        pay("20240001", id);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(tickets.count()).isEqualTo(1);
        assertThat(remaining(lunchMenuId)).isEqualTo(9);
    }

    @Test
    @DisplayName("입력 검증: 수량 범위, 시간대 식사 구분, 결제 수단(DR-01, DR-12)")
    void validation() throws Exception {
        mvc.perform(postJson("/api/reservations", Map.of("menuId", lunchMenuId, "slotId", noonSlotId, "quantity", 4))
                        .with(student("20240001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QTY_RANGE"));
        Integer dinnerSlot = timeSlots.findByMealTypeOrderByStartTimeAsc(MealType.DINNER).get(0).getSlotId();
        mvc.perform(postJson("/api/reservations", Map.of("menuId", lunchMenuId, "slotId", dinnerSlot, "quantity", 1))
                        .with(student("20240001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SLOT_MISMATCH"));
        int id = reserve("20240001", lunchMenuId, noonSlotId, 1);
        mvc.perform(postJson("/api/reservations/" + id + "/payment", Map.of("method", "CASH")).with(student("20240001")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_METHOD"));
    }

    @Test
    @DisplayName("확정 직전 재검사: 결제 화면에 머문 사이 품절되면 결제 없이 거절된다(FR-06)")
    void stockRecheckedOnConfirm() throws Exception {
        Integer menuId = createMenu("돈가스", 6000, MealType.LUNCH, 1).getMenuId();
        int first = reserve("20240001", menuId, noonSlotId, 1);
        int second = reserve("20240002", menuId, noonSlotId, 1);
        pay("20240001", first);
        mvc.perform(postJson("/api/reservations/" + second + "/payment", Map.of("method", "CARD")).with(student("20240002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_SHORTAGE"));
        assertThat(payments.count()).isEqualTo(1);
        assertThat(reservations.findById(second).orElseThrow().getStatus().name()).isEqualTo("PENDING");
        mvc.perform(get("/api/menus/today").with(student("20240002")))
                .andExpect(jsonPath("$[?(@.menuId == " + menuId + ")].soldOut").value(true));
    }

    @Test
    @DisplayName("시간대 정원은 수량이 아니라 건수로 센다(DR-04)")
    void slotCapacityCountsReservations() throws Exception {
        TimeSlot tiny = timeSlots.save(new TimeSlot(MealType.LUNCH, LocalTime.of(13, 0), LocalTime.of(13, 30), 1));
        int first = reserve("20240001", lunchMenuId, tiny.getSlotId(), 3);
        int second = reserve("20240002", lunchMenuId, tiny.getSlotId(), 1);
        pay("20240001", first);
        mvc.perform(postJson("/api/reservations/" + second + "/payment", Map.of("method", "CARD")).with(student("20240002")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"));
        mvc.perform(get("/api/menus/" + lunchMenuId + "/slots").with(student("20240002")))
                .andExpect(jsonPath("$.slots[?(@.slotId == " + tiny.getSlotId() + ")].full").value(true));
    }

    @Test
    @DisplayName("취소: 환불·재고 복구·식권 무효화, 중복 취소 거절, 취소 식권 수령 거절(FR-10~11, DR-07)")
    void cancel() throws Exception {
        int id = reserve("20240001", lunchMenuId, noonSlotId, 2);
        pay("20240001", id);
        mvc.perform(postJson("/api/reservations/" + id + "/cancel", Map.of()).with(student("20240001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.ticketStatus").value("INVALID"));
        assertThat(remaining(lunchMenuId)).isEqualTo(10);
        assertThat(payments.findAll().get(0).getStatus().name()).isEqualTo("REFUNDED");

        mvc.perform(postJson("/api/reservations/" + id + "/cancel", Map.of()).with(student("20240001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CANCELED"));
        mvc.perform(postJson("/api/admin/pickups", Map.of("code", qrOf(id))).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("취소된 예약의 식권입니다"));
        mvc.perform(get("/api/reservations/" + id + "/ticket/qr").with(student("20240001")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("취소 기한 경계: 12:00 시간대는 11:30:00 허용, 11:30:01 거절(DR-05)")
    void cancelDeadline() throws Exception {
        int a = reserve("20240001", lunchMenuId, noonSlotId, 1);
        pay("20240001", a);
        int b = reserve("20240001", lunchMenuId, noonSlotId, 1);
        pay("20240001", b);

        CLOCK.set(DAY.atTime(11, 30, 0));
        mvc.perform(postJson("/api/reservations/" + a + "/cancel", Map.of()).with(student("20240001")))
                .andExpect(status().isOk());
        CLOCK.set(DAY.atTime(11, 30, 1));
        mvc.perform(postJson("/api/reservations/" + b + "/cancel", Map.of()).with(student("20240001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE"));
    }

    @Test
    @DisplayName("메뉴 수정: 확보 수량 미만 거절, 예약 있는 메뉴의 날짜 변경 거절, 남은 수량 재계산(FR-13, DR-10~11)")
    void updateMenu() throws Exception {
        int id = reserve("20240001", lunchMenuId, noonSlotId, 3);
        pay("20240001", id);

        Map<String, Object> base = Map.of("name", "제육덮밥", "price", 6000, "saleDate", DAY.toString(),
                "mealType", "LUNCH", "totalQuantity", 2);
        mvc.perform(put("/api/admin/menus/" + lunchMenuId).with(csrf()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(base)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BELOW_SECURED"));

        Map<String, Object> moved = new java.util.HashMap<>(base);
        moved.put("totalQuantity", 20);
        moved.put("saleDate", DAY.plusDays(1).toString());
        mvc.perform(put("/api/admin/menus/" + lunchMenuId).with(csrf()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(moved)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATE_LOCKED"));

        Map<String, Object> ok = new java.util.HashMap<>(base);
        ok.put("totalQuantity", 20);
        mvc.perform(put("/api/admin/menus/" + lunchMenuId).with(csrf()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ok)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingQuantity").value(17))
                .andExpect(jsonPath("$.price").value(6000));
        assertThat(reservations.findById(id).orElseThrow().getTotalPrice()).isEqualTo(16500);
    }

    @Test
    @DisplayName("메뉴 등록 입력 검증과 등록(FR-12)")
    void registerMenu() throws Exception {
        mvc.perform(postJson("/api/admin/menus", Map.of("name", " ", "price", 5000, "saleDate", DAY.toString(),
                        "mealType", "DINNER", "totalQuantity", 10)).with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NAME_REQUIRED"));
        mvc.perform(postJson("/api/admin/menus", Map.of("name", "김치찌개", "price", 0, "saleDate", DAY.toString(),
                        "mealType", "DINNER", "totalQuantity", 10)).with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICE_RANGE"));
        mvc.perform(postJson("/api/admin/menus", Map.of("name", "김치찌개", "price", 5500, "saleDate", DAY.toString(),
                        "mealType", "DINNER", "totalQuantity", 10)).with(admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingQuantity").value(10));
    }

    @Test
    @DisplayName("동시성: 남은 수량 1개에 두 학생이 동시에 확정하면 1건만 성공한다(NFR-04)")
    void concurrentConfirmTakesLastStockOnce() throws Exception {
        Integer menuId = createMenu("비빔밥", 5000, MealType.LUNCH, 1).getMenuId();
        int a = reserve("20240001", menuId, noonSlotId, 1);
        int b = reserve("20240002", menuId, noonSlotId, 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> jobs = List.of(
                () -> { start.await(); return attempt("20240001", a); },
                () -> { start.await(); return attempt("20240002", b); });
        List<Future<String>> futures = new ArrayList<>();
        for (Callable<String> job : jobs) futures.add(pool.submit(job));
        start.countDown();
        List<String> results = new ArrayList<>();
        for (Future<String> f : futures) results.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("OK", "STOCK_SHORTAGE");
        assertThat(remaining(menuId)).isZero();
        assertThat(payments.count()).isEqualTo(1);
        assertThat(tickets.count()).isEqualTo(1);
    }

    private String attempt(String studentId, int reservationId) {
        try {
            reservationService.confirm(studentId, reservationId, "CARD");
            return "OK";
        } catch (BusinessException e) {
            return e.getCode();
        }
    }

    @Test
    @DisplayName("동시성: 같은 예약의 중복 취소는 환불·재고 복구를 1회만 반영한다(FR-11, NFR-05)")
    void concurrentCancelIsAppliedOnce() throws Exception {
        int id = reserve("20240001", lunchMenuId, noonSlotId, 2);
        pay("20240001", id);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> job = () -> {
            start.await();
            try {
                reservationService.cancel("20240001", id);
                return "OK";
            } catch (BusinessException e) {
                return e.getCode();
            }
        };
        Future<String> f1 = pool.submit(job);
        Future<String> f2 = pool.submit(job);
        start.countDown();
        List<String> results = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(results).containsExactlyInAnyOrder("OK", "ALREADY_CANCELED");
        assertThat(remaining(lunchMenuId)).isEqualTo(10);
    }

    @Test
    @DisplayName("오늘이 아닌 메뉴는 예약할 수 없다(DR-02)")
    void onlyToday() throws Exception {
        Admin admin = admins.findById("admin").orElseThrow();
        Integer tomorrow = menus.save(new Menu("내일메뉴", 5000, DAY.plusDays(1), MealType.LUNCH, 10, admin)).getMenuId();
        mvc.perform(postJson("/api/reservations", Map.of("menuId", tomorrow, "slotId", noonSlotId, "quantity", 1))
                        .with(student("20240001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_TODAY"));
        JsonNode today = json.readTree(mvc.perform(get("/api/menus/today").with(student("20240001")))
                .andReturn().getResponse().getContentAsString());
        assertThat(today.findValuesAsText("name")).doesNotContain("내일메뉴");
    }

    @SuppressWarnings("unused")
    private static LocalDateTime at(int h, int m, int s) {
        return DAY.atTime(h, m, s);
    }
}
