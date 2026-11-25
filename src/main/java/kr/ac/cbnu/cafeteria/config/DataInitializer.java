package kr.ac.cbnu.cafeteria.config;

import kr.ac.cbnu.cafeteria.domain.Admin;
import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.domain.Student;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import kr.ac.cbnu.cafeteria.repository.AdminRepository;
import kr.ac.cbnu.cafeteria.repository.MenuRepository;
import kr.ac.cbnu.cafeteria.repository.TimeSlotRepository;
import kr.ac.cbnu.cafeteria.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

/*=====================================================================
 파일명   : DataInitializer.java
 기능     : 초기 데이터. 계정과 수령 시간대·정원은 사전 등록한다는 운영 가정(시스템 개요)에 따라
            비어 있을 때만 만든다. 비밀번호는 BCrypt 해시로 저장한다(NFR-03).
            app.seed.demo-menus=true면 오늘 메뉴가 없을 때 시연용 메뉴를 만든다.
=====================================================================*/
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository users;
    private final AdminRepository admins;
    private final TimeSlotRepository timeSlots;
    private final MenuRepository menus;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final boolean seedAccounts;
    private final boolean seedDemoMenus;

    public DataInitializer(UserRepository users, AdminRepository admins, TimeSlotRepository timeSlots,
                           MenuRepository menus, PasswordEncoder passwordEncoder, Clock clock,
                           @Value("${app.seed.accounts:true}") boolean seedAccounts,
                           @Value("${app.seed.demo-menus:false}") boolean seedDemoMenus) {
        this.users = users;
        this.admins = admins;
        this.timeSlots = timeSlots;
        this.menus = menus;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.seedAccounts = seedAccounts;
        this.seedDemoMenus = seedDemoMenus;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedAccounts && users.count() == 0) {
            users.save(new Admin("admin", passwordEncoder.encode("admin1234"), "식당관리자"));
            users.save(new Student("20240001", passwordEncoder.encode("student1234"), "김학생", "소프트웨어학과"));
            users.save(new Student("20240002", passwordEncoder.encode("student1234"), "이학생", "컴퓨터공학과"));
            log.info("초기 계정 생성: admin / 20240001 / 20240002");
        }
        if (seedAccounts && timeSlots.count() == 0) {
            int capacity = 30;
            timeSlots.save(new TimeSlot(MealType.LUNCH, LocalTime.of(11, 30), LocalTime.of(12, 0), capacity));
            timeSlots.save(new TimeSlot(MealType.LUNCH, LocalTime.of(12, 0), LocalTime.of(12, 30), capacity));
            timeSlots.save(new TimeSlot(MealType.LUNCH, LocalTime.of(12, 30), LocalTime.of(13, 0), capacity));
            timeSlots.save(new TimeSlot(MealType.DINNER, LocalTime.of(17, 30), LocalTime.of(18, 0), capacity));
            timeSlots.save(new TimeSlot(MealType.DINNER, LocalTime.of(18, 0), LocalTime.of(18, 30), capacity));
            timeSlots.save(new TimeSlot(MealType.DINNER, LocalTime.of(18, 30), LocalTime.of(19, 0), capacity));
            log.info("수령 시간대 6개 생성(중식 3, 석식 3, 정원 {})", capacity);
        }
        LocalDate today = LocalDate.now(clock);
        if (seedDemoMenus && !menus.existsBySaleDate(today)) {
            Admin admin = admins.findById("admin").orElse(null);
            if (admin != null) {
                menus.save(new Menu("제육덮밥", 5500, today, MealType.LUNCH, 50, admin));
                menus.save(new Menu("돈가스", 6000, today, MealType.LUNCH, 40, admin));
                menus.save(new Menu("비빔밥", 5000, today, MealType.LUNCH, 30, admin));
                menus.save(new Menu("김치찌개", 5500, today, MealType.DINNER, 40, admin));
                menus.save(new Menu("치킨마요덮밥", 6000, today, MealType.DINNER, 30, admin));
                log.info("{} 시연용 메뉴 5개 생성", today);
            }
        }
    }
}
