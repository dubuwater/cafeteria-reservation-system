package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;

import java.time.LocalTime;

/** 시간대와 해당 날짜의 마감 여부(S03, FR-04) */
public record SlotDto(Integer slotId, MealType mealType, LocalTime startTime, LocalTime endTime,
                      int capacity, long reservedCount, boolean full) {

    public static SlotDto of(TimeSlot s, long reservedCount) {
        return new SlotDto(s.getSlotId(), s.getMealType(), s.getStartTime(), s.getEndTime(),
                s.getCapacity(), reservedCount, !s.hasCapacity(reservedCount));
    }
}
