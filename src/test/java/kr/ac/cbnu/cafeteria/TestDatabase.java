package kr.ac.cbnu.cafeteria;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 기본은 H2이며, 지정한 로컬 포트의 전용 MySQL 테스트 DB에서도 같은 테스트를 실행한다. */
abstract class TestDatabase {

    @DynamicPropertySource
    static void useMySqlWhenRequested(DynamicPropertyRegistry registry) {
        configureMySql(registry, "cafeteria_test");
    }

    static void configureMySql(DynamicPropertyRegistry registry, String database) {
        String port = System.getenv("CAFETERIA_TEST_MYSQL_PORT");
        if (port == null || port.isBlank()) {
            return;
        }
        int mysqlPort = Integer.parseInt(port);
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://127.0.0.1:" + mysqlPort + "/" + database + "?serverTimezone=Asia/Seoul"
                        + "&sessionVariables=innodb_lock_wait_timeout=5,transaction_isolation=%27READ-COMMITTED%27");
        registry.add("spring.datasource.username", () -> System.getenv("CAFETERIA_TEST_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("CAFETERIA_TEST_MYSQL_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-mysql.sql");
    }
}
