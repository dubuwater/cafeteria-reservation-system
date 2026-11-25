package kr.ac.cbnu.cafeteria.domain;

/** 예약 상태(결제대기, 예약완료, 수령완료, 취소) — 설계서 3.4절 */
public enum ReservationStatus {
    PENDING, RESERVED, PICKED_UP, CANCELED
}
