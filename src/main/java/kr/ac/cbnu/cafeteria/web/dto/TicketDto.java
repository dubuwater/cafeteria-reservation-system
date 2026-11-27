package kr.ac.cbnu.cafeteria.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.Ticket;
import kr.ac.cbnu.cafeteria.domain.TicketStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/** 식권 화면(S05)과 수령 처리 조회(A03)의 예약 정보. QR 문자열 자체는 담지 않는다. */
public record TicketDto(Integer ticketId, Integer reservationId, TicketStatus ticketStatus,
                        ReservationStatus reservationStatus, String studentName, String menuName,
                        int quantity, LocalDate saleDate,
                        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                        @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    public static TicketDto from(Ticket t) {
        var r = t.getReservation();
        return new TicketDto(t.getTicketId(), r.getReservationId(), t.getStatus(), r.getStatus(),
                r.getStudent().getName(), r.getMenu().getName(), r.getQuantity(), r.getMenu().getSaleDate(),
                r.getSlot().getStartTime(), r.getSlot().getEndTime());
    }
}
