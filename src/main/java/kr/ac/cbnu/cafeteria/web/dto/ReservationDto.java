package kr.ac.cbnu.cafeteria.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.TicketStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 예약 정보(S04 주문 요약, S06 목록). cancellable은 서버의 canCancel() 결과이다. */
public record ReservationDto(Integer reservationId, Integer menuId, String menuName, LocalDate saleDate,
                             MealType mealType, Integer slotId,
                             @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                             @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                             int quantity, int totalPrice, ReservationStatus status, LocalDateTime createdAt,
                             boolean cancellable, LocalDateTime cancelDeadline, TicketStatus ticketStatus) {

    public static ReservationDto of(Reservation r, LocalDateTime now) {
        return new ReservationDto(r.getReservationId(), r.getMenu().getMenuId(), r.getMenu().getName(),
                r.getMenu().getSaleDate(), r.getMenu().getMealType(), r.getSlot().getSlotId(),
                r.getSlot().getStartTime(), r.getSlot().getEndTime(), r.getQuantity(), r.getTotalPrice(),
                r.getStatus(), r.getCreatedAt(), r.canCancel(now), r.cancelDeadline(),
                r.getTicket() == null ? null : r.getTicket().getStatus());
    }
}
