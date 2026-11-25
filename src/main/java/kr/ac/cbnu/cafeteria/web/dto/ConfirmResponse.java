package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.PaymentStatus;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.TicketStatus;

import java.time.LocalDateTime;

/** 모의 결제와 예약 확정 응답(설계서 2.3절 예시 형식) */
public record ConfirmResponse(Integer reservationId, ReservationStatus status, int totalPrice,
                              PaymentInfo payment, TicketInfo ticket) {

    public record PaymentInfo(PaymentStatus status, LocalDateTime paidAt) {
    }

    public record TicketInfo(Integer ticketId, TicketStatus status) {
    }

    public static ConfirmResponse from(Reservation r) {
        return new ConfirmResponse(r.getReservationId(), r.getStatus(), r.getTotalPrice(),
                new PaymentInfo(r.getPayment().getStatus(), r.getPayment().getPaidAt()),
                new TicketInfo(r.getTicket().getTicketId(), r.getTicket().getStatus()));
    }
}
