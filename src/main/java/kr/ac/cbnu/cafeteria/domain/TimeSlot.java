package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;

/*=====================================================================
 파일명   : TimeSlot.java
 기능     : 수령 시간대(TimeSlot). 매일 반복되므로 날짜 없이 식사 구분과 시각, 정원만 저장한다.
            예약 건수(/reservedCount)는 저장하지 않는 파생 속성이다(설계서 3.2.5절, DR-04).
=====================================================================*/
@Entity
@Table(name = "time_slots")
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Integer slotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 10, nullable = false)
    private MealType mealType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    protected TimeSlot() {
    }

    public TimeSlot(MealType mealType, LocalTime startTime, LocalTime endTime, int capacity) {
        this.mealType = mealType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean TimeSlot::hasCapacity(long reservedCount)
    2) 매개변수: reservedCount = 해당 날짜·시간대의 예약완료·수령완료 건수(파생 속성 /reservedCount)
    3) 기능: 정원과 비교한다. 수량이 아니라 건수로 센다(DR-04). 설계서 4.5.1
             집계 질의는 CapacityService.hasCapacity(slot, date)가 수행한 뒤 이 메소드를 호출한다.
    4) 반환값: 정원 미만이면 true. 예: 정원 30, 29건 → true, 30건 → false
    ---------------------------------------------------------------------*/
    public boolean hasCapacity(long reservedCount) {
        return reservedCount < capacity;
    }

    public Integer getSlotId() {
        return slotId;
    }

    public MealType getMealType() {
        return mealType;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public int getCapacity() {
        return capacity;
    }
}
