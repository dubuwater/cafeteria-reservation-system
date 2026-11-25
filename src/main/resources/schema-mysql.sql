-- 대학교 학식 예약 시스템 데이터베이스 스키마 (설계서 3.1절)
-- MySQL 8.0, utf8mb4, InnoDB. 애플리케이션 시작 시 mysql 프로필에서 자동 실행된다.

CREATE TABLE IF NOT EXISTS users (
    user_id        VARCHAR(20)  NOT NULL,
    password_hash  CHAR(60)     NOT NULL,
    name           VARCHAR(20)  NOT NULL,
    role           ENUM('STUDENT','ADMIN') NOT NULL,
    department     VARCHAR(30)  NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS menus (
    menu_id            INT          NOT NULL AUTO_INCREMENT,
    name               VARCHAR(50)  NOT NULL,
    price              INT          NOT NULL,
    sale_date          DATE         NOT NULL,
    meal_type          ENUM('LUNCH','DINNER') NOT NULL,
    total_quantity     INT          NOT NULL,
    remaining_quantity INT          NOT NULL,
    admin_id           VARCHAR(20)  NOT NULL,
    PRIMARY KEY (menu_id),
    CONSTRAINT fk_menus_admin FOREIGN KEY (admin_id) REFERENCES users (user_id),
    CONSTRAINT ck_menus_price CHECK (price > 0),
    CONSTRAINT ck_menus_total CHECK (total_quantity >= 1),
    CONSTRAINT ck_menus_remaining CHECK (remaining_quantity >= 0 AND remaining_quantity <= total_quantity),
    INDEX idx_menus_date_meal (sale_date, meal_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS time_slots (
    slot_id    INT  NOT NULL AUTO_INCREMENT,
    meal_type  ENUM('LUNCH','DINNER') NOT NULL,
    start_time TIME NOT NULL,
    end_time   TIME NOT NULL,
    capacity   INT  NOT NULL,
    PRIMARY KEY (slot_id),
    CONSTRAINT ck_slots_time CHECK (end_time > start_time),
    CONSTRAINT ck_slots_capacity CHECK (capacity >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id INT         NOT NULL AUTO_INCREMENT,
    student_id     VARCHAR(20) NOT NULL,
    menu_id        INT         NOT NULL,
    slot_id        INT         NOT NULL,
    quantity       TINYINT     NOT NULL,
    total_price    INT         NOT NULL,
    status         ENUM('PENDING','RESERVED','PICKED_UP','CANCELED') NOT NULL DEFAULT 'PENDING',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reservation_id),
    CONSTRAINT fk_res_student FOREIGN KEY (student_id) REFERENCES users (user_id),
    CONSTRAINT fk_res_menu    FOREIGN KEY (menu_id)    REFERENCES menus (menu_id),
    CONSTRAINT fk_res_slot    FOREIGN KEY (slot_id)    REFERENCES time_slots (slot_id),
    CONSTRAINT ck_res_quantity CHECK (quantity BETWEEN 1 AND 3),
    INDEX idx_res_student (student_id, created_at),
    INDEX idx_res_slot_status (slot_id, status),
    INDEX idx_res_menu_status (menu_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payments (
    payment_id     INT         NOT NULL AUTO_INCREMENT,
    reservation_id INT         NOT NULL,
    amount         INT         NOT NULL,
    method         VARCHAR(20) NOT NULL,
    approval_no    VARCHAR(40) NOT NULL,
    status         ENUM('PAID','REFUNDED') NOT NULL,
    paid_at        DATETIME    NOT NULL,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uq_payments_reservation (reservation_id),
    CONSTRAINT fk_pay_res FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id),
    CONSTRAINT ck_pay_method CHECK (method IN ('CARD','SIMPLE_PAY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tickets (
    ticket_id      INT      NOT NULL AUTO_INCREMENT,
    reservation_id INT      NOT NULL,
    qr_code        CHAR(36) NOT NULL,
    issued_at      DATETIME NOT NULL,
    status         ENUM('VALID','USED','INVALID') NOT NULL DEFAULT 'VALID',
    PRIMARY KEY (ticket_id),
    UNIQUE KEY uq_tickets_reservation (reservation_id),
    UNIQUE KEY uq_tickets_qr (qr_code),
    CONSTRAINT fk_ticket_res FOREIGN KEY (reservation_id) REFERENCES reservations (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
