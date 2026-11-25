package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;

/** 예약 현황 상세 목록(A02) */
public record AdminReservationDto(Integer reservationId, String studentName, String menuName,
                                  int quantity, ReservationStatus status) {

    public static AdminReservationDto from(Reservation r) {
        return new AdminReservationDto(r.getReservationId(), r.getStudent().getName(), r.getMenu().getName(),
                r.getQuantity(), r.getStatus());
    }
}
