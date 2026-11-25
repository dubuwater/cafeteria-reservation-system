package kr.ac.cbnu.cafeteria.web.dto;

public record ReservationRequest(Integer menuId, Integer slotId, Integer quantity) {
}
