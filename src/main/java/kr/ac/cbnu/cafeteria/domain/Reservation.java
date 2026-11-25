package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/*=====================================================================
 파일명   : Reservation.java
 기능     : 예약 도메인 클래스. 결제대기 예약의 확정, 취소, 수령 상태 전이를 담당한다(설계서 3.2.6절).
            여러 행을 잠그는 확정·취소 트랜잭션의 순서 제어는 ReservationService가 하고,
            이 클래스는 상태 전이 규칙만 가진다.
 관련 요구 : FR-05~07, FR-10~11, DR-01, DR-05~09, DR-11
=====================================================================*/
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_res_student", columnList = "student_id, created_at"),
        @Index(name = "idx_res_slot_status", columnList = "slot_id, status"),
        @Index(name = "idx_res_menu_status", columnList = "menu_id, status")
})
public class Reservation {

    /** 수량 상한(DR-01), 설계서 3.4절 상수 */
    public static final int MAX_QUANTITY = 3;
    /** 취소 마감: 시간대 시작 30분 전(DR-05) */
    public static final int CANCEL_DEADLINE_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Integer reservationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private TimeSlot slot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 결제(0..1, 합성). 결제대기 예약에는 없다. */
    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;

    /** 식권(0..1, 합성). 결제대기 예약에는 없다. */
    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Ticket ticket;

    protected Reservation() {
    }

    /** 결제대기(PENDING) 예약을 만든다. 총액은 표시용으로 먼저 계산한다(설계서 4.2.2의 5단계). */
    public Reservation(Student student, Menu menu, TimeSlot slot, int quantity, LocalDateTime createdAt) {
        this.student = student;
        this.menu = menu;
        this.slot = slot;
        this.quantity = quantity;
        this.status = ReservationStatus.PENDING;
        this.createdAt = createdAt;
        this.totalPrice = calculateTotal();
    }

    /*---------------------------------------------------------------------
    1) 메소드명: int Reservation::calculateTotal()
    3) 기능: 현재 메뉴 단가 × 수량. 확정 시점에 한 번 더 호출해 저장한다(DR-11). 설계서 4.6.1
    ---------------------------------------------------------------------*/
    public int calculateTotal() {
        return menu.getPrice() * quantity;
    }

    /**
     * 확정 처리(설계서 4.6.2의 3·5·6단계). 결제가 승인된 뒤, 잠금을 건 트랜잭션 안에서만 호출한다.
     * 재고 차감, 결제·식권 연결, 상태 변경을 한 번에 반영한다.
     */
    public void confirm(Payment payment, Ticket ticket) {
        if (status != ReservationStatus.PENDING) {
            throw BusinessException.conflict("BAD_STATE", "확정할 수 없는 예약입니다");
        }
        this.totalPrice = calculateTotal();
        menu.decreaseStock(quantity);
        this.payment = payment;
        this.ticket = ticket;
        this.status = ReservationStatus.RESERVED;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Reservation::canCancel(LocalDateTime now)
    2) 매개변수: now = 서버 현재 일시(클라이언트 시각은 쓰지 않는다)
    3) 기능: 예약완료 상태이고 '판매 날짜 + 시작 시각 − 30분' 이하인지 확인한다(DR-05). 설계서 4.6.3
    4) 반환값: 취소 가능하면 true. 경계값 포함: 12:00 시작이면 11:30:00 허용, 11:30:01 거절
    ---------------------------------------------------------------------*/
    public boolean canCancel(LocalDateTime now) {
        if (status != ReservationStatus.RESERVED) {
            return false;
        }
        return !now.isAfter(cancelDeadline());
    }

    /** 취소 마감 일시 = 판매 날짜와 시간대 시작 시각을 합친 일시 − 30분 */
    public LocalDateTime cancelDeadline() {
        return LocalDateTime.of(menu.getSaleDate(), slot.getStartTime()).minusMinutes(CANCEL_DEADLINE_MINUTES);
    }

    /**
     * 취소 상태 전이(설계서 4.6.4의 5단계). 환불이 성공한 뒤, 잠금을 건 트랜잭션 안에서만 호출한다.
     * 재고 복구와 식권 무효화를 함께 반영한다. 시간대 예약 건수는 상태 변경으로 집계에서 자동 제외된다.
     */
    public void markCanceled() {
        menu.increaseStock(quantity);
        if (ticket != null) {
            ticket.invalidate();
        }
        this.status = ReservationStatus.CANCELED;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: void Reservation::complete()
    3) 기능: 예약완료 → 수령완료. 수령 처리 트랜잭션 안에서만 호출한다(FR-15, DR-09). 설계서 4.6.5
    ---------------------------------------------------------------------*/
    public void complete() {
        if (status != ReservationStatus.RESERVED) {
            throw BusinessException.conflict("BAD_STATE", "수령할 수 없는 예약입니다");
        }
        this.status = ReservationStatus.PICKED_UP;
    }

    public boolean isOwnedBy(String studentId) {
        return student.getUserId().equals(studentId);
    }

    public Integer getReservationId() {
        return reservationId;
    }

    public Student getStudent() {
        return student;
    }

    public Menu getMenu() {
        return menu;
    }

    public TimeSlot getSlot() {
        return slot;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getTotalPrice() {
        return totalPrice;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Payment getPayment() {
        return payment;
    }

    public Ticket getTicket() {
        return ticket;
    }
}
