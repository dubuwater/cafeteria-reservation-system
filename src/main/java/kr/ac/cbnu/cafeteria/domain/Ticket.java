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

import java.time.LocalDateTime;

/*=====================================================================
 파일명   : Ticket.java
 기능     : QR 식권(Ticket). 예약 1건당 최대 1개, QR에는 추측할 수 없는 UUID만 담는다(설계서 3.2.8절).
 관련 요구 : FR-07, FR-09, FR-15, DR-07~08
=====================================================================*/
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Integer ticketId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(name = "qr_code", length = 36, nullable = false, unique = true)
    private String qrCode;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private TicketStatus status;

    protected Ticket() {
    }

    /** 유효(VALID) 식권을 발급한다. qrCode는 TicketService.generateQR()이 만든 고유 값이다. */
    public Ticket(Reservation reservation, String qrCode, LocalDateTime issuedAt) {
        this.reservation = reservation;
        this.qrCode = qrCode;
        this.issuedAt = issuedAt;
        this.status = TicketStatus.VALID;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Ticket::verify(String qrCode)
    2) 매개변수: qrCode = 스캔하거나 예약 번호로 찾은 식권의 QR 문자열
    3) 기능: QR 일치, 유효 상태, 예약완료 상태를 모두 확인한다(UC-14, DR-08). 설계서 4.8.2
    ---------------------------------------------------------------------*/
    public boolean verify(String qrCode) {
        if (!this.qrCode.equals(qrCode)) {
            return false;                                       // 다른 식권
        }
        if (status != TicketStatus.VALID) {
            return false;                                       // 사용 또는 무효
        }
        return reservation.getStatus() == ReservationStatus.RESERVED;  // 수령완료·취소면 거절
    }

    /*---------------------------------------------------------------------
    1) 메소드명: void Ticket::markUsed()
    3) 기능: 유효한 식권을 사용 처리한다(DR-08). 설계서 4.8.3
    ---------------------------------------------------------------------*/
    public void markUsed() {
        if (status != TicketStatus.VALID) {
            throw BusinessException.conflict("ALREADY_USED", "이미 수령된 식권입니다");
        }
        status = TicketStatus.USED;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: void Ticket::invalidate()
    3) 기능: 예약 취소 시 식권을 무효 처리한다. 유효 상태일 때만 바꾼다(DR-07). 설계서 4.8.4
    ---------------------------------------------------------------------*/
    public void invalidate() {
        if (status == TicketStatus.VALID) {
            status = TicketStatus.INVALID;
        }
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public String getQrCode() {
        return qrCode;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public TicketStatus getStatus() {
        return status;
    }
}
