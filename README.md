# 대학교 학식 예약 시스템

학생이 오늘의 학식을 미리 예약하고 모의 결제 후 QR 식권으로 수령하는 웹 시스템입니다.
소프트웨어공학 Term Project의 요구사항명세서와 소프트웨어 설계명세서를 기준으로 구현했습니다.

- 학생 화면(모바일 세로): 로그인, 오늘의 메뉴, 예약하기, 결제, 식권(QR), 내 예약
- 관리자 화면(PC·태블릿 가로): 메뉴 관리, 예약 현황, 수령 처리(QR 스캔 또는 예약 번호 입력)

## 실행 환경

| 구분 | 버전 |
| --- | --- |
| Java | 17 이상 |
| 빌드 | Gradle 8.14 (Wrapper 포함, 별도 설치 불필요) |
| 프레임워크 | Spring Boot 3.5, Spring Security, Spring Data JPA |
| DB | MySQL 8.0 (설계서 기준) 또는 H2 (설치 없이 로컬 실행) |
| 라이브러리 | ZXing(QR 생성), html5-qrcode(QR 스캔) |

## 빠른 실행 (설치 없이, H2)

```bash
./gradlew bootRun          # Windows: gradlew.bat bootRun
```

브라우저에서 http://localhost:8080 에 접속합니다. 데이터는 `./data/` 폴더의 H2 파일에 저장되어 재시작해도 유지됩니다.

| 역할 | 아이디 | 비밀번호 |
| --- | --- | --- |
| 관리자 | `admin` | `admin1234` |
| 학생 | `20240001` | `student1234` |
| 학생 | `20240002` | `student1234` |

계정과 수령 시간대(중식 11:30·12:00·12:30, 석식 17:30·18:00·18:30, 정원 30)는 DB가 비어 있을 때 자동으로 만들어지고,
로컬 실행 시 오늘 날짜의 시연용 메뉴 5개도 함께 생성됩니다.

> 스마트폰으로 학생 화면을 볼 때는 같은 Wi-Fi에서 `http://<PC의 IP>:8080` 으로 접속합니다.
> 관리자 화면의 카메라 스캔은 브라우저 정책상 `localhost` 또는 HTTPS에서만 동작하므로,
> 다른 기기에서 스캔하려면 아래 HTTPS 설정을 사용하거나 예약 번호 직접 입력을 사용하세요.

## MySQL로 실행 (설계서 기준 환경, NFR-09)

```sql
CREATE DATABASE cafeteria_db DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'cafeteria'@'localhost' IDENTIFIED BY 'cafeteria';
GRANT ALL PRIVILEGES ON cafeteria_db.* TO 'cafeteria'@'localhost';
```

```bash
./gradlew bootRun --args='--spring.profiles.active=mysql'
# 계정 정보를 바꾸려면 DB_USERNAME, DB_PASSWORD 환경 변수를 지정합니다.
```

테이블은 시작 시 `src/main/resources/schema-mysql.sql`(설계서 3.1절 스키마, `CREATE TABLE IF NOT EXISTS`)로 만들어집니다.
MySQL 프로필에서는 시연용 메뉴를 만들지 않으므로 관리자 화면에서 메뉴를 등록하세요.

## 클라우드 배포 (Render 무료 플랜, 시연용)

저장소의 `Dockerfile`과 `render.yaml`로 Render에 배포합니다. HTTPS 주소가 자동으로 생겨서
휴대폰 카메라로도 QR을 스캔할 수 있습니다.

1. https://render.com 에 GitHub 계정으로 가입합니다(카드 등록 불필요).
2. 대시보드에서 **New → Blueprint**를 고르고 이 저장소를 연결합니다.
3. `render.yaml`이 인식되면 **Apply**를 누릅니다. 첫 빌드는 5~10분 걸립니다.
4. 완료되면 `https://cafeteria-reservation-xxxx.onrender.com` 형태의 주소로 접속합니다.

`main` 브랜치에 push하면 자동으로 다시 배포됩니다.

**무료 플랜 주의사항**

- 15분 동안 접속이 없으면 서버가 잠들고, 다음 접속 때 깨어나는 데 1분 정도 걸립니다. 시연 몇 분 전에 미리 접속해 두세요.
- `render` 프로필은 메모리 DB를 씁니다. 서버가 잠들거나 재배포되면 예약 기록이 사라지고, 깨어날 때 계정·시간대·그날 메뉴가 새로 만들어집니다.
- 서버 시각은 Asia/Seoul 기준입니다.

로컬에서 배포 설정을 시험하려면 `--spring.profiles.active=render`로 실행합니다(HTTPS 프록시 뒤를 전제로 쿠키에 Secure가 붙으므로, http://localhost 에서는 로그인이 유지되지 않습니다).

### 배포용 HTTPS (EIR-NW-01)

```bash
keytool -genkeypair -alias cafeteria -keyalg RSA -keysize 2048 -storetype PKCS12 \
        -keystore keystore.p12 -validity 365
./gradlew bootJar
SSL_KEYSTORE=file:keystore.p12 SSL_KEYSTORE_PASSWORD=<암호> \
  java -jar build/libs/cafeteria.jar --spring.profiles.active=mysql,prod
```

`prod` 프로필은 443 포트, HTTPS, Secure 쿠키를 켭니다.

### 백업과 복원 (NFR-08)

```bash
mysqldump --single-transaction cafeteria_db | gzip > cafeteria_$(date +%Y%m%d).sql.gz
gunzip < cafeteria_YYYYMMDD.sql.gz | mysql cafeteria_db
```

## 테스트

```bash
./gradlew test
```

24개 테스트가 설계서의 업무 규칙과 비기능 요구사항을 검증합니다.

| 테스트 | 검증 내용 |
| --- | --- |
| `DomainRulesTest` | 재고 차감 시점(DR-03), 정원 경계값 29·30건(DR-04), 취소 기한 11:30:00 허용·11:30:01 거절(DR-05), 식권 1회 사용(DR-08), 가격 변경 후 총액 유지(DR-11) |
| `ReservationFlowIntegrationTest` | 로그인·로그아웃, 접근 통제(401/403), CSRF, 예약→결제→식권→수령 전체 흐름, 결제 재전송 1회 반영, 확정 직전 재고·정원 재검사, 취소·환불, 메뉴 수정 제약, **남은 수량 1개에 동시 확정 시 1건만 성공**, 중복 취소 1회 반영 |

## 프로젝트 구조

```text
src/main/java/kr/ac/cbnu/cafeteria
 ├─ web          AuthController, StudentController, AdminController, GlobalExceptionHandler, dto/*
 ├─ service      StudentService, AdminService, ReservationService, AuthService, TimeSlotService, LockSupport
 ├─ domain       User, Student, Admin, Menu, TimeSlot, Reservation, Payment, Ticket, 열거형, BusinessException
 ├─ repository   UserRepository ~ TicketRepository (Spring Data JPA, 행 잠금 질의)
 ├─ payment      PaymentGateway, MockPaymentGateway
 ├─ qr           QrImageWriter
 └─ config       SecurityConfig, ClockConfig(Asia/Seoul), DataInitializer
src/main/resources
 ├─ application.yml       local(H2) · mysql · prod 프로필
 ├─ schema-mysql.sql      설계서 3.1절 스키마
 └─ static                login.html, student/*.html, admin/*.html, css/app.css, js/common.js
```

## 설계서와 코드의 대응

| 설계서 | 구현 위치 |
| --- | --- |
| 4.1 User::login / logout | `AuthService.login`, `AuthController`, `SecurityConfig`(logout) |
| 4.2 Student 메소드 | `StudentService` (viewTodayMenus, makeReservation, getMyReservations, cancelReservation) |
| 4.3 Admin 메소드 | `AdminService` (registerMenu, updateMenu, viewReservationStatus, confirmPickup) |
| 4.4 Menu 메소드 | `Menu` (isAvailable, decreaseStock, increaseStock) |
| 4.5 TimeSlot::hasCapacity | `TimeSlotService.hasCapacity`(집계 질의) + `TimeSlot.hasCapacity`(정원 비교) |
| 4.6 Reservation 메소드 | `Reservation`(calculateTotal, canCancel, complete, 상태 전이) + `ReservationService`(confirm, cancel 트랜잭션·잠금) |
| 4.7 Payment 메소드 | `Payment.pay`, `Payment.refund` |
| 4.8 Ticket 메소드 | `Ticket`(verify, markUsed, invalidate), `ReservationService.generateQR` |
| 4.9 MockPaymentGateway | `payment/MockPaymentGateway` |
| 5장 화면 알고리즘 | `static/js/common.js`(api 함수), 각 화면 HTML의 스크립트 |
| 6장 화면 S01~S06, A01~A03 | `static/login.html`, `static/student/*`, `static/admin/*` |

리포지토리가 필요한 메소드(로그인, 정원 집계, 확정·취소처럼 여러 행을 잠그는 처리)는 서비스 클래스에 두고,
엔티티에는 상태와 업무 규칙만 남겼습니다. 알고리즘의 처리 순서와 잠금 순서(예약 → 메뉴 → 시간대 → 식권)는 설계서와 같습니다.

### 설계서와 달라진 점

- 모의 승인 번호는 `MOCK-` 뒤에 하이픈 없는 UUID(총 37자)를 씁니다. 설계서의 `MOCK-` + UUID(41자)는 `approval_no VARCHAR(40)`을 넘어서 테스트에서 저장 오류가 났기 때문입니다.
- 화면에서 쓰기 위해 API 3개를 추가했습니다: `GET /api/auth/me`(로그인 상태 확인), `GET /api/reservations/{id}`(결제 화면 주문 요약), `GET /api/admin/reservations?date=&slotId=`(예약 현황 상세). QR 이미지는 `GET /api/reservations/{id}/ticket/qr`로 분리했습니다.
- 초기 계정은 `data.sql` 대신 `DataInitializer`가 BCrypt 해시로 만듭니다.
- 설계서의 CSRF 헤더 이름은 Spring Security 기본값인 `X-XSRF-TOKEN`을 씁니다.
- 로컬 실행용으로 H2(MySQL 호환 모드) 프로필을 추가했습니다.
