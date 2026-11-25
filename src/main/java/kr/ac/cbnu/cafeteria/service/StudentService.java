package kr.ac.cbnu.cafeteria.service;

import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.Student;
import kr.ac.cbnu.cafeteria.domain.Ticket;
import kr.ac.cbnu.cafeteria.domain.TicketStatus;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import kr.ac.cbnu.cafeteria.qr.QrImageWriter;
import kr.ac.cbnu.cafeteria.repository.MenuRepository;
import kr.ac.cbnu.cafeteria.repository.ReservationRepository;
import kr.ac.cbnu.cafeteria.repository.StudentRepository;
import kr.ac.cbnu.cafeteria.repository.TimeSlotRepository;
import kr.ac.cbnu.cafeteria.web.dto.MenuDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuSlotsResponse;
import kr.ac.cbnu.cafeteria.web.dto.ReservationDto;
import kr.ac.cbnu.cafeteria.web.dto.SlotDto;
import kr.ac.cbnu.cafeteria.web.dto.TicketDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/*=====================================================================
 파일명   : StudentService.java
 기능     : 학생(Student) 메소드 알고리즘(설계서 4.2절). 모든 메소드는 세션의 학생 아이디로만
            동작하며, 다른 학생의 예약·식권은 서버에서 차단한다(NFR-02).
 관련 요구 : FR-03~05, FR-08~10, DR-01~02, DR-12
=====================================================================*/
@Service
public class StudentService {

    private final StudentRepository students;
    private final MenuRepository menus;
    private final TimeSlotRepository timeSlots;
    private final ReservationRepository reservations;
    private final TimeSlotService timeSlotService;
    private final ReservationService reservationService;
    private final QrImageWriter qrImageWriter;
    private final Clock clock;

    public StudentService(StudentRepository students, MenuRepository menus, TimeSlotRepository timeSlots,
                          ReservationRepository reservations, TimeSlotService timeSlotService,
                          ReservationService reservationService, QrImageWriter qrImageWriter, Clock clock) {
        this.students = students;
        this.menus = menus;
        this.timeSlots = timeSlots;
        this.reservations = reservations;
        this.timeSlotService = timeSlotService;
        this.reservationService = reservationService;
        this.qrImageWriter = qrImageWriter;
        this.clock = clock;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: List<Menu> Student::viewTodayMenus()
    3) 기능: 서버 기준 오늘 판매하는 메뉴를 중식·석식 순으로 조회한다(FR-03, DR-02). 설계서 4.2.1
       남은 수량이 0인 메뉴도 '품절' 표시를 위해 그대로 반환한다.
    ---------------------------------------------------------------------*/
    @Transactional(readOnly = true)
    public List<MenuDto> viewTodayMenus() {
        LocalDate today = LocalDate.now(clock);
        return menus.findBySaleDateOrderByMenuIdAsc(today).stream()
                .sorted(Comparator.comparing(Menu::getMealType).thenComparing(Menu::getMenuId))
                .map(MenuDto::from)
                .toList();
    }

    /** S03: 메뉴 정보와 식사 구분에 맞는 시간대, 각 시간대의 마감 여부(FR-04, DR-12) */
    @Transactional(readOnly = true)
    public MenuSlotsResponse slotsForMenu(Integer menuId) {
        Menu menu = findTodayMenu(menuId);
        Map<Integer, long[]> counts = timeSlotService.countsByDate(menu.getSaleDate());
        List<SlotDto> slots = timeSlotService.slotsFor(menu.getMealType()).stream()
                .map(s -> SlotDto.of(s, counts.getOrDefault(s.getSlotId(), new long[]{0, 0})[0]))
                .toList();
        return new MenuSlotsResponse(MenuDto.from(menu), slots);
    }

    /*---------------------------------------------------------------------
    1) 메소드명: Reservation Student::makeReservation(Menu menu, TimeSlot slot, int qty)
    2) 매개변수: studentId = 세션의 학생; menuId, slotId = 선택값; qty = 수량
    3) 기능: 1차 검사 후 결제대기(PENDING) 예약을 만든다. 재고와 정원은 확보하지 않는다
             (FR-04~05, DR-01~03, DR-12). 설계서 4.2.2
    4) 반환값: 생성된 예약. 조건 위반 시 예외
    ---------------------------------------------------------------------*/
    @Transactional
    public ReservationDto makeReservation(String studentId, Integer menuId, Integer slotId, Integer qty) {
        if (menuId == null || slotId == null || qty == null) {
            throw BusinessException.invalid("REQUIRED", "메뉴, 수량, 수령 시간대를 선택하세요");
        }
        Menu menu = menus.findById(menuId)
                .orElseThrow(() -> BusinessException.notFound("NO_MENU", "메뉴를 찾을 수 없습니다"));
        TimeSlot slot = timeSlots.findById(slotId)
                .orElseThrow(() -> BusinessException.notFound("NO_SLOT", "수령 시간대를 찾을 수 없습니다"));
        // 1. 오늘 판매 메뉴만 예약할 수 있다(DR-02).
        if (!menu.getSaleDate().equals(LocalDate.now(clock))) {
            throw BusinessException.conflict("NOT_TODAY", "오늘 판매 메뉴만 예약할 수 있습니다");
        }
        // 2. 수량은 1~MAX_QUANTITY(3)개이다(DR-01).
        if (qty < 1 || qty > Reservation.MAX_QUANTITY) {
            throw BusinessException.invalid("QTY_RANGE", "수량은 1~3개입니다");
        }
        // 3. 시간대의 식사 구분이 메뉴와 같아야 한다(DR-12).
        if (slot.getMealType() != menu.getMealType()) {
            throw BusinessException.invalid("SLOT_MISMATCH", "선택할 수 없는 시간대입니다");
        }
        // 4. 화면 안내용 1차 검사. 최종 검사는 confirm()에서 잠금을 건 뒤 다시 한다.
        if (!menu.isAvailable(qty)) {
            throw BusinessException.conflict("STOCK_SHORTAGE", "남은 수량이 부족합니다");
        }
        if (!timeSlotService.hasCapacity(slot, menu.getSaleDate())) {
            throw BusinessException.conflict("SLOT_FULL", "선택한 시간대가 마감되었습니다");
        }
        // 5~6. 결제대기 예약을 만들고 저장한다. 총액은 생성자에서 표시용으로 계산한다.
        Student student = students.getReferenceById(studentId);
        LocalDateTime now = LocalDateTime.now(clock);
        Reservation r = reservations.save(new Reservation(student, menu, slot, qty, now));
        return ReservationDto.of(r, now);
    }

    /*---------------------------------------------------------------------
    1) 메소드명: List<Reservation> Student::getMyReservations()
    3) 기능: 본인 예약을 최신순으로 조회하고 결제대기 건은 제외한다. 항목마다 취소 가능 여부를
             함께 계산해 S06 취소 버튼 활성화에 쓴다(FR-08, NFR-02). 설계서 4.2.3
    ---------------------------------------------------------------------*/
    @Transactional(readOnly = true)
    public List<ReservationDto> getMyReservations(String studentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return reservations.findMine(studentId, ReservationStatus.PENDING).stream()
                .map(r -> ReservationDto.of(r, now))
                .toList();
    }

    /** 본인 예약 1건(S04 주문 요약) */
    @Transactional(readOnly = true)
    public ReservationDto getReservation(String studentId, Integer reservationId) {
        return ReservationDto.of(findOwned(studentId, reservationId), LocalDateTime.now(clock));
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Student::cancelReservation(int reservationId)
    3) 기능: 예약 소유자를 확인한 뒤 Reservation::cancel()을 호출한다(FR-10, NFR-02). 설계서 4.2.4
       소유자 확인과 취소는 ReservationService.cancel()이 잠금을 건 같은 트랜잭션에서 한다.
    ---------------------------------------------------------------------*/
    public boolean cancelReservation(String studentId, Integer reservationId) {
        return reservationService.cancel(studentId, reservationId);
    }

    /** S05: 본인 식권 정보(FR-09). 결제대기 예약에는 식권이 없다. */
    @Transactional(readOnly = true)
    public TicketDto getTicket(String studentId, Integer reservationId) {
        Reservation r = findOwned(studentId, reservationId);
        Ticket ticket = r.getTicket();
        if (ticket == null) {
            throw BusinessException.notFound("NO_TICKET", "발급된 식권이 없습니다");
        }
        return TicketDto.from(ticket);
    }

    /** S05: QR 이미지. 유효한 식권만 이미지를 내어 준다(FR-09, EIR-SW-05). */
    @Transactional(readOnly = true)
    public byte[] getTicketQr(String studentId, Integer reservationId) {
        Ticket ticket = findOwned(studentId, reservationId).getTicket();
        if (ticket == null) {
            throw BusinessException.notFound("NO_TICKET", "발급된 식권이 없습니다");
        }
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw BusinessException.conflict("TICKET_UNUSABLE", "사용할 수 없는 식권입니다");
        }
        return qrImageWriter.toPng(ticket.getQrCode());
    }

    private Menu findTodayMenu(Integer menuId) {
        Menu menu = menus.findById(menuId)
                .orElseThrow(() -> BusinessException.notFound("NO_MENU", "메뉴를 찾을 수 없습니다"));
        if (!menu.getSaleDate().equals(LocalDate.now(clock))) {
            throw BusinessException.conflict("NOT_TODAY", "오늘 판매 메뉴만 예약할 수 있습니다");
        }
        return menu;
    }

    private Reservation findOwned(String studentId, Integer reservationId) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> BusinessException.notFound("NO_RESERVATION", "예약을 찾을 수 없습니다"));
        if (!r.isOwnedBy(studentId)) {
            throw BusinessException.forbidden();
        }
        return r;
    }
}
