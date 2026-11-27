package kr.ac.cbnu.cafeteria.web;

import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.service.AdminService;
import kr.ac.cbnu.cafeteria.web.dto.AdminReservationDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuRequest;
import kr.ac.cbnu.cafeteria.web.dto.PickupRequest;
import kr.ac.cbnu.cafeteria.web.dto.SlotStatusDto;
import kr.ac.cbnu.cafeteria.web.dto.TicketDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 관리자 API(설계서 5.0절). ADMIN 역할만 접근한다(SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** UC-11 메뉴 목록(A01) */
    @GetMapping("/menus")
    public List<MenuDto> menus(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) MealType mealType) {
        return adminService.listMenus(date, mealType);
    }

    /** UC-11 메뉴 등록(A01) */
    @PostMapping("/menus")
    public MenuDto register(@RequestBody MenuRequest req, Authentication auth) {
        return adminService.registerMenu(auth.getName(), req);
    }

    /** UC-11 메뉴 수정(A01) */
    @PutMapping("/menus/{menuId}")
    public MenuDto update(@PathVariable Integer menuId, @RequestBody MenuRequest req) {
        return adminService.updateMenu(menuId, req);
    }

    /** UC-12 시간대별 예약 현황(A02) */
    @GetMapping("/reservations/status")
    public List<SlotStatusDto> status(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return adminService.viewReservationStatus(date);
    }

    /** UC-12 시간대 상세(A02) */
    @GetMapping("/reservations")
    public List<AdminReservationDto> reservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer slotId) {
        return adminService.reservationsForSlot(date, slotId);
    }

    /** UC-13 스캔·입력 후 예약 정보 조회(A03, 상태 변경 없음) */
    @GetMapping("/tickets/lookup")
    public TicketDto lookup(@RequestParam String code) {
        return adminService.lookup(code);
    }

    /** UC-13~14 수령 처리(A03) */
    @PostMapping("/pickups")
    public TicketDto pickup(@RequestBody PickupRequest req) {
        return adminService.confirmPickup(req.code());
    }
}
