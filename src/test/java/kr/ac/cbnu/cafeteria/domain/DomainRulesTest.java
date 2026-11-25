package kr.ac.cbnu.cafeteria.domain;

import kr.ac.cbnu.cafeteria.payment.MockPaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 도메인 클래스의 업무 규칙 단위 테스트(경계값 분석, 상태 기반) */
class DomainRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 26);

    private Admin admin;
    private Student student;
    private Menu menu;
    private TimeSlot noon;
    private MockPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        admin = new Admin("admin", "hash", "관리자");
        student = new Student("20240001", "hash", "김학생", "소프트웨어학과");
        menu = new Menu("제육덮밥", 5500, DAY, MealType.LUNCH, 10, admin);
        noon = new TimeSlot(MealType.LUNCH, LocalTime.of(12, 0), LocalTime.of(12, 30), 30);
        gateway = new MockPaymentGateway(false);
    }

    private Reservation confirmed(int qty) {
        Reservation r = new Reservation(student, menu, noon, qty, DAY.atTime(10, 0));
        Payment p = Payment.pay(r, r.calculateTotal(), "CARD", gateway, DAY.atTime(10, 1));
        r.confirm(p, new Ticket(r, "11111111-2222-3333-4444-555555555555", DAY.atTime(10, 1)));
        return r;
    }

    @Test
    @DisplayName("결제대기 예약은 재고를 차지하지 않고, 확정 시 수량만큼 차감한다(DR-03)")
    void stockIsTakenOnlyOnConfirm() {
        Reservation r = new Reservation(student, menu, noon, 3, DAY.atTime(10, 0));
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(menu.getRemainingQuantity()).isEqualTo(10);
        assertThat(r.getTotalPrice()).isEqualTo(16500);

        Reservation c = confirmed(3);
        assertThat(c.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(menu.getRemainingQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("남은 수량보다 많이 차감할 수 없고, 복구는 총 판매 수량을 넘지 않는다")
    void stockBounds() {
        assertThat(menu.isAvailable(10)).isTrue();
        assertThat(menu.isAvailable(11)).isFalse();
        assertThatThrownBy(() -> menu.decreaseStock(11)).isInstanceOf(BusinessException.class);
        menu.increaseStock(5);
        assertThat(menu.getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("정원은 건수로 비교한다: 정원 30에 29건은 여유, 30건은 마감(DR-04)")
    void capacityBoundary() {
        assertThat(noon.hasCapacity(29)).isTrue();
        assertThat(noon.hasCapacity(30)).isFalse();
    }

    @Test
    @DisplayName("취소 기한: 12:00 시작이면 11:30:00은 허용, 11:30:01은 거절(DR-05)")
    void cancelDeadlineBoundary() {
        Reservation r = confirmed(1);
        assertThat(r.canCancel(DAY.atTime(11, 30, 0))).isTrue();
        assertThat(r.canCancel(DAY.atTime(11, 30, 1))).isFalse();
        assertThat(r.canCancel(DAY.minusDays(1).atTime(23, 0))).isTrue();
    }

    @Test
    @DisplayName("결제대기·수령완료 예약은 취소할 수 없다")
    void cancelOnlyReserved() {
        Reservation pending = new Reservation(student, menu, noon, 1, DAY.atTime(10, 0));
        assertThat(pending.canCancel(DAY.atTime(9, 0))).isFalse();
        Reservation r = confirmed(1);
        r.complete();
        assertThat(r.canCancel(DAY.atTime(9, 0))).isFalse();
    }

    @Test
    @DisplayName("취소하면 재고 복구, 식권 무효화, 결제는 한 번만 환불된다(DR-07, FR-11)")
    void cancelEffects() {
        Reservation r = confirmed(2);
        assertThat(menu.getRemainingQuantity()).isEqualTo(8);
        assertThat(r.getPayment().refund(gateway)).isTrue();
        r.markCanceled();
        assertThat(menu.getRemainingQuantity()).isEqualTo(10);
        assertThat(r.getTicket().getStatus()).isEqualTo(TicketStatus.INVALID);
        assertThat(r.getPayment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(r.getPayment().refund(gateway)).isFalse();
    }

    @Test
    @DisplayName("식권은 유효한 예약완료 건에서 1회만 사용할 수 있다(DR-08)")
    void ticketSingleUse() {
        Reservation r = confirmed(1);
        Ticket t = r.getTicket();
        assertThat(t.verify("다른-QR")).isFalse();
        assertThat(t.verify(t.getQrCode())).isTrue();
        t.markUsed();
        r.complete();
        assertThat(t.verify(t.getQrCode())).isFalse();
        assertThatThrownBy(t::markUsed).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("확정 후 메뉴 가격이 바뀌어도 예약 총액은 그대로다(DR-11)")
    void priceChangeDoesNotAffectTotal() {
        Reservation r = confirmed(2);
        menu.update("제육덮밥", 7000, DAY, MealType.LUNCH, 10, 2);
        assertThat(r.getTotalPrice()).isEqualTo(11000);
        assertThat(menu.getRemainingQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("허용되지 않은 결제 수단이나 거절된 승인은 결제를 만들지 않는다")
    void paymentRejected() {
        Reservation r = new Reservation(student, menu, noon, 1, DAY.atTime(10, 0));
        assertThat(Payment.pay(r, 5500, "CASH", gateway, DAY.atTime(10, 0))).isNull();
        assertThat(Payment.pay(r, 5500, "CARD", new MockPaymentGateway(true), DAY.atTime(10, 0))).isNull();
    }
}
