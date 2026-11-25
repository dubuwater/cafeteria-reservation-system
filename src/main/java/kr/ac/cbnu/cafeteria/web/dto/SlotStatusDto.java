package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.MealType;

import java.time.LocalTime;

/** 시간대별 예약 현황(A02, FR-14). 설계서 3.4절 SlotStatus */
public record SlotStatusDto(Integer slotId, MealType mealType, LocalTime startTime, LocalTime endTime,
                            int capacity, long reservedCount, long pickedUpCount) {
}
