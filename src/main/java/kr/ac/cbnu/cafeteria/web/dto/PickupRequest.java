package kr.ac.cbnu.cafeteria.web.dto;

/** 수령 처리 입력: QR 문자열 또는 예약 번호(A03) */
public record PickupRequest(String code) {
}
