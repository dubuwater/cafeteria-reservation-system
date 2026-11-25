package kr.ac.cbnu.cafeteria.repository;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Integer> {

    List<TimeSlot> findByMealTypeOrderByStartTimeAsc(MealType mealType);
}
