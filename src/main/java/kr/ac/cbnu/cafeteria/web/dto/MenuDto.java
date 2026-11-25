package kr.ac.cbnu.cafeteria.web.dto;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;

import java.time.LocalDate;

public record MenuDto(Integer menuId, String name, int price, LocalDate saleDate, MealType mealType,
                      int totalQuantity, int remainingQuantity, boolean soldOut) {

    public static MenuDto from(Menu m) {
        return new MenuDto(m.getMenuId(), m.getName(), m.getPrice(), m.getSaleDate(), m.getMealType(),
                m.getTotalQuantity(), m.getRemainingQuantity(), m.getRemainingQuantity() == 0);
    }
}
