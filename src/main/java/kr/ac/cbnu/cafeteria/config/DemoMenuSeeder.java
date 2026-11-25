package kr.ac.cbnu.cafeteria.config;

import kr.ac.cbnu.cafeteria.domain.Admin;
import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.repository.AdminRepository;
import kr.ac.cbnu.cafeteria.repository.MenuRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*=====================================================================
 파일명   : DemoMenuSeeder.java
 기능     : 시연용 메뉴 생성. 요일마다 다른 메뉴 세트(중식 3개, 석식 2개)를 그날 날짜로 만든다.
            서버 시작 시와 매일 0시 1분(Asia/Seoul)에 오늘 메뉴가 없으면 만든다.
            app.seed.demo-menus=true일 때만 동작한다(local, render 프로필).
=====================================================================*/
@Component
public class DemoMenuSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoMenuSeeder.class);

    /** 메뉴 이름, 가격(원), 판매 수량 */
    record DemoMenu(String name, int price, int quantity) {
    }

    /** 요일별 메뉴 세트: [중식 3개, 석식 2개] */
    record DaySet(List<DemoMenu> lunch, List<DemoMenu> dinner) {
    }

    static final Map<DayOfWeek, DaySet> WEEKLY = new EnumMap<>(DayOfWeek.class);

    static {
        WEEKLY.put(DayOfWeek.MONDAY, new DaySet(
                List.of(new DemoMenu("제육덮밥", 5500, 50), new DemoMenu("돈가스", 6000, 40), new DemoMenu("비빔밥", 5000, 30)),
                List.of(new DemoMenu("김치찌개", 5500, 40), new DemoMenu("치킨마요덮밥", 6000, 30))));
        WEEKLY.put(DayOfWeek.TUESDAY, new DaySet(
                List.of(new DemoMenu("불고기덮밥", 6000, 45), new DemoMenu("오므라이스", 5500, 35), new DemoMenu("유부우동", 4500, 30)),
                List.of(new DemoMenu("된장찌개", 5000, 40), new DemoMenu("닭갈비덮밥", 6000, 30))));
        WEEKLY.put(DayOfWeek.WEDNESDAY, new DaySet(
                List.of(new DemoMenu("카레라이스", 5000, 50), new DemoMenu("짜장면", 5000, 40), new DemoMenu("순두부찌개", 5500, 30)),
                List.of(new DemoMenu("김치나베돈가스", 6500, 35), new DemoMenu("비빔국수", 5000, 30))));
        WEEKLY.put(DayOfWeek.THURSDAY, new DaySet(
                List.of(new DemoMenu("치즈돈가스", 6500, 40), new DemoMenu("콩나물국밥", 4500, 40), new DemoMenu("참치마요덮밥", 5000, 35)),
                List.of(new DemoMenu("부대찌개", 6000, 40), new DemoMenu("오징어덮밥", 5500, 30))));
        WEEKLY.put(DayOfWeek.FRIDAY, new DaySet(
                List.of(new DemoMenu("소불고기비빔밥", 6000, 45), new DemoMenu("냉모밀", 5000, 30), new DemoMenu("김치볶음밥", 5000, 40)),
                List.of(new DemoMenu("제육쌈밥", 6000, 35), new DemoMenu("라면·김밥 세트", 4500, 40))));
        WEEKLY.put(DayOfWeek.SATURDAY, new DaySet(
                List.of(new DemoMenu("돈가스카레", 6000, 30), new DemoMenu("잔치국수", 4500, 30), new DemoMenu("새우볶음밥", 5500, 25)),
                List.of(new DemoMenu("감자탕", 6500, 25), new DemoMenu("치킨마요덮밥", 6000, 25))));
        WEEKLY.put(DayOfWeek.SUNDAY, new DaySet(
                List.of(new DemoMenu("떡국", 5000, 30), new DemoMenu("치킨까스", 6000, 30), new DemoMenu("비빔밥", 5000, 25)),
                List.of(new DemoMenu("미역국 정식", 5000, 30), new DemoMenu("제육덮밥", 5500, 25))));
    }

    private final MenuRepository menus;
    private final AdminRepository admins;
    private final Clock clock;
    private final boolean enabled;

    public DemoMenuSeeder(MenuRepository menus, AdminRepository admins, Clock clock,
                          @Value("${app.seed.demo-menus:false}") boolean enabled) {
        this.menus = menus;
        this.admins = admins;
        this.clock = clock;
        this.enabled = enabled;
    }

    /** 매일 0시 1분: 서버가 자정을 넘겨 켜져 있어도 새 날짜의 메뉴가 생기도록 한다. */
    @Scheduled(cron = "0 1 0 * * *", zone = "${app.timezone:Asia/Seoul}")
    @Transactional
    public void seedToday() {
        if (enabled) {
            seedFor(LocalDate.now(clock));
        }
    }

    /** 해당 날짜에 메뉴가 하나도 없을 때만 그 요일의 메뉴 세트를 만든다. 만든 개수를 반환한다. */
    @Transactional
    public int seedFor(LocalDate date) {
        if (menus.existsBySaleDate(date)) {
            return 0;
        }
        Admin admin = admins.findById("admin").orElse(null);
        if (admin == null) {
            return 0;
        }
        DaySet set = WEEKLY.get(date.getDayOfWeek());
        set.lunch().forEach(m -> menus.save(new Menu(m.name(), m.price(), date, MealType.LUNCH, m.quantity(), admin)));
        set.dinner().forEach(m -> menus.save(new Menu(m.name(), m.price(), date, MealType.DINNER, m.quantity(), admin)));
        int count = set.lunch().size() + set.dinner().size();
        log.info("{}({}) 시연용 메뉴 {}개 생성", date, date.getDayOfWeek(), count);
        return count;
    }
}
