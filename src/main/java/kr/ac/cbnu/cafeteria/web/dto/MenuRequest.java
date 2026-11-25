package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.MealType;

import java.time.LocalDate;

/** 메뉴 등록·수정 입력(A01, FR-12~13) */
public record MenuRequest(String name, Integer price, LocalDate saleDate, MealType mealType, Integer totalQuantity) {
}
