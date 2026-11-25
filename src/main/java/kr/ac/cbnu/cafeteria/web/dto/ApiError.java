package kr.ac.cbnu.cafeteria.web.dto;

/** 모든 오류 응답 본문(EIR-NW-03). message는 화면에 그대로 표시한다. */
public record ApiError(String code, String message) {
}
