package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kr.ac.cbnu.cafeteria.payment.ApprovalResult;
import kr.ac.cbnu.cafeteria.payment.PaymentGateway;

import java.time.LocalDateTime;
import java.util.Set;

/*=====================================================================
 파일명   : Payment.java
 기능     : 결제(Payment). 결제 모듈(PaymentGateway)을 통해 모의 승인·환불을 한다(설계서 3.2.7절).
            reservation_id UNIQUE 제약이 예약 1건당 결제 1건을 보장한다(DR-06, NFR-05).
=====================================================================*/
@Entity
@Table(name = "payments")
public class Payment {

    public static final Set<String> METHODS = Set.of("CARD", "SIMPLE_PAY");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Integer paymentId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "method", length = 20, nullable = false)
    private String method;

    @Column(name = "approval_no", length = 40, nullable = false)
    private String approvalNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    protected Payment() {
    }

    private Payment(Reservation reservation, int amount, String method, String approvalNo, LocalDateTime paidAt) {
        this.reservation = reservation;
        this.amount = amount;
        this.method = method;
        this.approvalNo = approvalNo;
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: Payment Payment::pay(Reservation, int amount, String method, PaymentGateway, LocalDateTime)
    2) 매개변수: amount = 결제 금액(확정 당시 총액); method = 'CARD' 또는 'SIMPLE_PAY';
                gateway = 결제 모듈; now = 서버 현재 일시
    3) 기능: 결제 수단을 확인하고 결제 모듈에 승인을 요청한다. 승인되면 결제완료 Payment를 만든다(FR-06).
             설계서 4.7.1
    4) 반환값: 결제완료 Payment. 수단이 잘못됐거나 승인이 거절되면 null(호출자가 전체 롤백)
    ---------------------------------------------------------------------*/
    public static Payment pay(Reservation reservation, int amount, String method,
                              PaymentGateway gateway, LocalDateTime now) {
        // 1. 허용된 결제 수단인지 확인한다.
        if (method == null || !METHODS.contains(method)) {
            return null;
        }
        // 2. 인터페이스를 통해 승인을 요청한다. 과제에서는 MockPaymentGateway가 응답한다.
        ApprovalResult result = gateway.approve(amount, method);
        if (!result.approved()) {
            return null;
        }
        // 3. 승인 정보를 기록한다. 저장은 Reservation과 함께 cascade로 반영된다.
        return new Payment(reservation, amount, method, result.approvalNo(), now);
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Payment::refund(PaymentGateway gateway)
    3) 기능: 결제 금액 전액을 환불한다. 결제완료 상태만 환불해 이중 환불을 막는다(FR-11, DR-07). 설계서 4.7.2
    4) 반환값: 환불되면 true, 이미 환불됐거나 모듈이 거절하면 false
    ---------------------------------------------------------------------*/
    public boolean refund(PaymentGateway gateway) {
        if (status != PaymentStatus.PAID) {
            return false;
        }
        if (!gateway.refund(approvalNo, amount)) {
            return false;
        }
        status = PaymentStatus.REFUNDED;
        return true;
    }

    public Integer getPaymentId() {
        return paymentId;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public int getAmount() {
        return amount;
    }

    public String getMethod() {
        return method;
    }

    public String getApprovalNo() {
        return approvalNo;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }
}
