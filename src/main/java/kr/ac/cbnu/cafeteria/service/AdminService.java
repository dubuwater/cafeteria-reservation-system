package kr.ac.cbnu.cafeteria.service;

import kr.ac.cbnu.cafeteria.domain.Admin;
import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.MealType;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.Ticket;
import kr.ac.cbnu.cafeteria.domain.TicketStatus;
import kr.ac.cbnu.cafeteria.repository.AdminRepository;
import kr.ac.cbnu.cafeteria.repository.MenuRepository;
import kr.ac.cbnu.cafeteria.repository.ReservationRepository;
import kr.ac.cbnu.cafeteria.repository.TicketRepository;
import kr.ac.cbnu.cafeteria.web.dto.AdminReservationDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuRequest;
import kr.ac.cbnu.cafeteria.web.dto.SlotStatusDto;
import kr.ac.cbnu.cafeteria.web.dto.TicketDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/*=====================================================================
 파일명   : AdminService.java
 기능     : 관리자(Admin) 메소드 알고리즘(설계서 4.3절). 메뉴 등록·수정, 시간대별 예약 현황, 수령 처리
 관련 요구 : FR-12~15, DR-04, DR-08, DR-10~11
=====================================================================*/
@Service
public class AdminService {

    private final AdminRepository admins;
    private final MenuRepository menus;
    private final ReservationRepository reservations;
    private final TicketRepository tickets;
    private final TimeSlotService timeSlotService;
    private final LockSupport locks;
    private final Clock clock;

    public AdminService(AdminRepository admins, MenuRepository menus, ReservationRepository reservations,
                        TicketRepository tickets, TimeSlotService timeSlotService, LockSupport locks, Clock clock) {
        this.admins = admins;
        this.menus = menus;
        this.reservations = reservations;
        this.tickets = tickets;
        this.timeSlotService = timeSlotService;
        this.locks = locks;
        this.clock = clock;
    }

    /** A01: 날짜·식사 구분별 메뉴 목록(날짜 기본값 오늘) */
    @Transactional(readOnly = true)
    public List<MenuDto> listMenus(LocalDate date, MealType mealType) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        List<Menu> list = mealType == null
                ? menus.findBySaleDateOrderByMenuIdAsc(d)
                : menus.findBySaleDateAndMealTypeOrderByMenuIdAsc(d, mealType);
        return list.stream().map(MenuDto::from).toList();
    }

    /*---------------------------------------------------------------------
    1) 메소드명: int Admin::registerMenu(Menu menu)
    2) 매개변수: adminId = 세션의 관리자; req = 판매 날짜, 식사 구분, 이름, 가격, 판매 수량
    3) 기능: 입력값을 검사하고 남은 수량을 총 판매 수량으로 초기화해 등록한다(FR-12). 설계서 4.3.1
    4) 반환값: 등록된 메뉴(생성된 menuId 포함)
    ---------------------------------------------------------------------*/
    @Transactional
    public MenuDto registerMenu(String adminId, MenuRequest req) {
        // 1. 필수 값과 범위를 검사한다.
        validate(req);
        // 2~3. 남은 수량 = 총 판매 수량으로 만들고 등록 관리자를 기록해 저장한다.
        Admin admin = admins.getReferenceById(adminId);
        Menu menu = new Menu(req.name().trim(), req.price(), req.saleDate(), req.mealType(),
                req.totalQuantity(), admin);
        return MenuDto.from(menus.save(menu));
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Admin::updateMenu(Menu newMenu)
    2) 매개변수: menuId = 수정할 메뉴; req = 새 값
    3) 기능: 확보 수량보다 작은 판매 수량, 예약이 있는 메뉴의 날짜·식사 구분 변경을 막고 메뉴를 수정한다
             (FR-13, DR-10~11). 설계서 4.3.2
    4) 반환값: 수정된 메뉴. 실패 시 예외
    ---------------------------------------------------------------------*/
    @Transactional
    public MenuDto updateMenu(Integer menuId, MenuRequest req) {
        // 1. 입력값 검사는 registerMenu()의 1단계와 같다.
        validate(req);
        // 2. 수정 중 다른 학생의 확정이 끼어들지 않도록 메뉴 행을 잠근다.
        Menu old = locks.findForUpdate(Menu.class, menuId);
        if (old == null) {
            throw BusinessException.notFound("NO_MENU", "메뉴를 찾을 수 없습니다");
        }
        // 3. 확보 수량(예약완료·수령완료 수량 합계)을 구한다.
        int secured = (int) reservations.sumQuantityByMenu(menuId, TimeSlotService.SECURED);
        // 4. 판매 수량은 확보 수량 이상이어야 한다(DR-10).
        if (req.totalQuantity() < secured) {
            throw BusinessException.conflict("BELOW_SECURED",
                    "이미 확보된 수량(" + secured + "개)보다 작게 할 수 없습니다");
        }
        // 5. 확보된 예약이 있으면 판매 날짜와 식사 구분은 바꿀 수 없다(FR-13).
        if (secured > 0 && (!req.saleDate().equals(old.getSaleDate()) || req.mealType() != old.getMealType())) {
            throw BusinessException.conflict("DATE_LOCKED", "예약이 있는 메뉴의 날짜·식사 구분은 바꿀 수 없습니다");
        }
        // 6. 값을 바꾸고 남은 수량을 다시 계산한다. 기존 예약의 total_price는 갱신하지 않는다(DR-11).
        old.update(req.name().trim(), req.price(), req.saleDate(), req.mealType(), req.totalQuantity(), secured);
        return MenuDto.from(old);
    }

    /*---------------------------------------------------------------------
    1) 메소드명: List<SlotStatus> Admin::viewReservationStatus(LocalDate date)
    3) 기능: 시간대별 정원, 예약 건수, 수령완료 건수를 질의 1회로 집계한다. 예약이 없는 시간대도
             0건으로 표시한다(FR-14, DR-04). 설계서 4.3.3
    ---------------------------------------------------------------------*/
    @Transactional(readOnly = true)
    public List<SlotStatusDto> viewReservationStatus(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        Map<Integer, long[]> counts = timeSlotService.countsByDate(d);
        return timeSlotService.allSlots().stream()
                .map(s -> {
                    long[] c = counts.getOrDefault(s.getSlotId(), new long[]{0, 0});
                    return new SlotStatusDto(s.getSlotId(), s.getMealType(), s.getStartTime(), s.getEndTime(),
                            s.getCapacity(), c[0], c[1]);
                })
                .toList();
    }

    /** A02 상세: 시간대 행을 누르면 예약 번호, 학생 이름, 메뉴, 수량, 상태(결제대기 제외) */
    @Transactional(readOnly = true)
    public List<AdminReservationDto> reservationsForSlot(LocalDate date, Integer slotId) {
        LocalDate d = date == null ? LocalDate.now(clock) : date;
        return reservations.findForSlot(slotId, d, ReservationStatus.PENDING).stream()
                .map(AdminReservationDto::from)
                .toList();
    }

    /** A03: 스캔·입력 직후 예약 정보 조회. 상태는 바꾸지 않는다. */
    @Transactional(readOnly = true)
    public TicketDto lookup(String code) {
        return TicketDto.from(findTicket(code));
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Admin::confirmPickup(String qrOrReservationId)
    2) 매개변수: code = QR에서 읽은 UUID 문자열 또는 직접 입력한 예약 번호
    3) 기능: 식권을 찾아 검증하고 식권은 사용, 예약은 수령완료로 바꾼다(FR-15, DR-08). 설계서 4.3.4
             두 경로 모두 식권의 qrCode로 같은 verify()를 거치므로 직접 입력에도 같은 검증이 적용된다.
    4) 반환값: 수령 처리 후 식권 정보. 실패 시 예외
    ---------------------------------------------------------------------*/
    @Transactional
    public TicketDto confirmPickup(String code) {
        // 1~2. 입력 형태로 경로를 나눠 식권을 찾는다. 없으면(없는 번호, 결제대기 예약) 거절한다.
        Ticket found = findTicket(code);
        // 3. 예약 → 식권 순서로 잠근다.
        Reservation r = reservations.findByIdForUpdate(found.getReservation().getReservationId())
                .orElseThrow(() -> BusinessException.notFound("NO_TICKET", "유효하지 않은 식권입니다"));
        Ticket ticket = locks.lock(found);
        // 4. 두 경로 모두 식권의 qrCode로 같은 검증을 한다(DR-08).
        if (!ticket.verify(ticket.getQrCode())) {
            if (ticket.getStatus() == TicketStatus.USED || r.getStatus() == ReservationStatus.PICKED_UP) {
                throw BusinessException.conflict("ALREADY_USED", "이미 수령된 식권입니다");
            }
            throw BusinessException.conflict("INVALID_TICKET", "취소된 예약의 식권입니다");
        }
        // 5. 식권 사용 처리와 예약 수령완료를 함께 반영한다.
        ticket.markUsed();
        r.complete();
        return TicketDto.from(ticket);
    }

    /** 예약 번호는 숫자만, QR은 UUID(영문·하이픈)이므로 입력 형태로 경로를 나눈다. */
    private Ticket findTicket(String code) {
        String input = code == null ? "" : code.trim();
        if (input.isEmpty()) {
            throw BusinessException.invalid("REQUIRED", "QR을 스캔하거나 예약 번호를 입력하세요");
        }
        if (input.chars().allMatch(Character::isDigit)) {
            if (input.length() > 9) {
                throw BusinessException.notFound("NO_TICKET", "유효하지 않은 식권입니다");
            }
            return tickets.findByReservationId(Integer.valueOf(input))
                    .orElseThrow(() -> BusinessException.notFound("NO_TICKET", "유효하지 않은 식권입니다"));
        }
        return tickets.findByQrCode(input)
                .orElseThrow(() -> BusinessException.notFound("NO_TICKET", "유효하지 않은 식권입니다"));
    }

    private void validate(MenuRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw BusinessException.invalid("NAME_REQUIRED", "메뉴 이름은 필수입니다");
        }
        if (req.name().trim().length() > 50) {
            throw BusinessException.invalid("NAME_LENGTH", "메뉴 이름은 50자 이하여야 합니다");
        }
        if (req.price() == null || req.price() <= 0) {
            throw BusinessException.invalid("PRICE_RANGE", "가격은 0원보다 커야 합니다");
        }
        if (req.totalQuantity() == null || req.totalQuantity() < 1) {
            throw BusinessException.invalid("QTY_RANGE", "판매 수량은 1개 이상이어야 합니다");
        }
        if (req.saleDate() == null || req.mealType() == null) {
            throw BusinessException.invalid("DATE_REQUIRED", "판매 날짜와 식사 구분을 선택하세요");
        }
    }
}
