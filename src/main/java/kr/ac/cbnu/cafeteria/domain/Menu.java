package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/*=====================================================================
 파일명   : Menu.java
 기능     : 메뉴(Menu). 판매 날짜·식사 구분별 메뉴와 남은 수량을 관리한다(설계서 3.2.4절).
 관련 요구 : FR-03, FR-12~13, DR-02~03, DR-07, DR-10
=====================================================================*/
@Entity
@Table(name = "menus", indexes = @Index(name = "idx_menus_date_meal", columnList = "sale_date, meal_type"))
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_id")
    private Integer menuId;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 10, nullable = false)
    private MealType mealType;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    protected Menu() {
    }

    /** 새 메뉴. 아직 확보된 예약이 없으므로 남은 수량 = 총 판매 수량이다(설계서 4.3.1). */
    public Menu(String name, int price, LocalDate saleDate, MealType mealType, int totalQuantity, Admin admin) {
        this.name = name;
        this.price = price;
        this.saleDate = saleDate;
        this.mealType = mealType;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.admin = admin;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Menu::isAvailable(int qty)
    2) 매개변수: qty = 요청 수량
    3) 기능: 남은 수량이 요청 수량 이상인지 확인한다(FR-04, DR-03). 설계서 4.4.1
    4) 반환값: 가능하면 true
    ---------------------------------------------------------------------*/
    public boolean isAvailable(int qty) {
        return remainingQuantity >= qty;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: void Menu::decreaseStock(int qty)
    3) 기능: 예약 확정 시 남은 수량을 줄인다. 잠금을 건 확정 트랜잭션 안에서만 호출한다(DR-03). 설계서 4.4.2
    ---------------------------------------------------------------------*/
    public void decreaseStock(int qty) {
        // 잠금 아래에서 다시 확인한다(방어 코드).
        if (remainingQuantity - qty < 0) {
            throw BusinessException.conflict("STOCK_SHORTAGE", "남은 수량이 부족합니다");
        }
        remainingQuantity -= qty;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: void Menu::increaseStock(int qty)
    3) 기능: 예약 취소 시 남은 수량을 복구한다. 총 판매 수량을 넘지 않는다(DR-07). 설계서 4.4.3
    ---------------------------------------------------------------------*/
    public void increaseStock(int qty) {
        remainingQuantity = Math.min(remainingQuantity + qty, totalQuantity);
    }

    /**
     * 메뉴 정보를 바꾸고 남은 수량을 다시 계산한다(설계서 4.3.2의 6단계).
     * 확보 수량 검사와 날짜·식사 구분 변경 검사는 호출하는 쪽(AdminService)이 먼저 한다.
     */
    public void update(String name, int price, LocalDate saleDate, MealType mealType,
                       int totalQuantity, int securedQuantity) {
        this.name = name;
        this.price = price;
        this.saleDate = saleDate;
        this.mealType = mealType;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity - securedQuantity;
    }

    public Integer getMenuId() {
        return menuId;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public MealType getMealType() {
        return mealType;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public Admin getAdmin() {
        return admin;
    }
}
