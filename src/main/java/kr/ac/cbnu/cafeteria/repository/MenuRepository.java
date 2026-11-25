package kr.ac.cbnu.cafeteria.repository;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Integer> {

    /** SQL> SELECT … FROM menus WHERE sale_date = :date ORDER BY menu_id (idx_menus_date_meal) */
    List<Menu> findBySaleDateOrderByMenuIdAsc(LocalDate saleDate);

    List<Menu> findBySaleDateAndMealTypeOrderByMenuIdAsc(LocalDate saleDate, MealType mealType);

    boolean existsBySaleDate(LocalDate saleDate);
}
