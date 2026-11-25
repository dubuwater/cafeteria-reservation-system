package kr.ac.cbnu.cafeteria.service;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import kr.ac.cbnu.cafeteria.repository.ReservationRepository;
import kr.ac.cbnu.cafeteria.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*=====================================================================
 파일명   : TimeSlotService.java
 기능     : 수령 시간대 정원 집계. 예약 건수(/reservedCount)는 저장하지 않고
            예약완료·수령완료 건만 세어 구한다(DR-04, 설계서 4.5.1).
=====================================================================*/
@Service
public class TimeSlotService {

    /** 정원과 확보 수량에 포함되는 상태: 예약완료, 수령완료 */
    public static final Set<ReservationStatus> SECURED = EnumSet.of(ReservationStatus.RESERVED, ReservationStatus.PICKED_UP);

    private final TimeSlotRepository timeSlots;
    private final ReservationRepository reservations;

    public TimeSlotService(TimeSlotRepository timeSlots, ReservationRepository reservations) {
        this.timeSlots = timeSlots;
        this.reservations = reservations;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean TimeSlot::hasCapacity(LocalDate date) — 집계 부분
    2) 매개변수: slot = 시간대; date = 메뉴의 판매 날짜
    3) 기능: 해당 날짜·시간대의 예약 건수를 집계해 정원과 비교한다. 수량이 아니라 건수로 센다.
       SQL> SELECT COUNT(*) FROM reservations r JOIN menus m ON r.menu_id = m.menu_id
            WHERE r.slot_id = :slotId AND m.sale_date = :date AND r.status IN ('RESERVED','PICKED_UP');
    4) 반환값: 정원에 여유가 있으면 true
    ---------------------------------------------------------------------*/
    public boolean hasCapacity(TimeSlot slot, LocalDate date) {
        long reservedCount = reservations.countBySlotAndDate(slot.getSlotId(), date, SECURED);
        return slot.hasCapacity(reservedCount);
    }

    /** 식사 구분에 맞는 시간대 목록(시작 시각 순, DR-12) */
    public List<TimeSlot> slotsFor(MealType mealType) {
        return timeSlots.findByMealTypeOrderByStartTimeAsc(mealType);
    }

    /** 전체 시간대(중식 → 석식, 시작 시각 순) */
    public List<TimeSlot> allSlots() {
        return timeSlots.findAll().stream()
                .sorted(Comparator.comparing(TimeSlot::getMealType).thenComparing(TimeSlot::getStartTime))
                .toList();
    }

    /** 날짜별 시간대 집계 [예약 건수, 수령완료 건수]. 질의 1회로 모든 시간대를 센다(설계서 4.3.3). */
    public Map<Integer, long[]> countsByDate(LocalDate date) {
        Map<Integer, long[]> result = new HashMap<>();
        for (Object[] row : reservations.aggregateBySlot(date, SECURED, ReservationStatus.PICKED_UP)) {
            long reserved = ((Number) row[1]).longValue();
            long picked = row[2] == null ? 0 : ((Number) row[2]).longValue();
            result.put((Integer) row[0], new long[]{reserved, picked});
        }
        return result;
    }
}
