package kr.ac.cbnu.cafeteria.web;

import kr.ac.cbnu.cafeteria.service.ReservationService;
import kr.ac.cbnu.cafeteria.service.StudentService;
import kr.ac.cbnu.cafeteria.web.dto.ConfirmResponse;
import kr.ac.cbnu.cafeteria.web.dto.MenuDto;
import kr.ac.cbnu.cafeteria.web.dto.MenuSlotsResponse;
import kr.ac.cbnu.cafeteria.web.dto.PaymentRequest;
import kr.ac.cbnu.cafeteria.web.dto.ReservationDto;
import kr.ac.cbnu.cafeteria.web.dto.ReservationRequest;
import kr.ac.cbnu.cafeteria.web.dto.TicketDto;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 학생 API(설계서 5.0절). 세션의 학생 아이디로만 동작하며, 소유권은 서비스에서 확인한다(NFR-02).
 * Controller는 업무 규칙을 갖지 않는다.
 */
@RestController
public class StudentController {

    private final StudentService studentService;
    private final ReservationService reservationService;

    public StudentController(StudentService studentService, ReservationService reservationService) {
        this.studentService = studentService;
        this.reservationService = reservationService;
    }

    /** UC-03 오늘의 메뉴(S02) */
    @GetMapping("/api/menus/today")
    public List<MenuDto> todayMenus() {
        return studentService.viewTodayMenus();
    }

    /** UC-04 시간대와 마감 여부(S03) */
    @GetMapping("/api/menus/{menuId}/slots")
    public MenuSlotsResponse slots(@PathVariable Integer menuId) {
        return studentService.slotsForMenu(menuId);
    }

    /** UC-04 결제대기 예약 생성(S03) */
    @PostMapping("/api/reservations")
    public ReservationDto reserve(@RequestBody ReservationRequest req, Authentication auth) {
        return studentService.makeReservation(auth.getName(), req.menuId(), req.slotId(), req.quantity());
    }

    /** S04 주문 요약 */
    @GetMapping("/api/reservations/{id}")
    public ReservationDto reservation(@PathVariable Integer id, Authentication auth) {
        return studentService.getReservation(auth.getName(), id);
    }

    /** UC-05~06 모의 결제와 예약 확정(S04) */
    @PostMapping("/api/reservations/{id}/payment")
    public ConfirmResponse pay(@PathVariable Integer id, @RequestBody PaymentRequest req, Authentication auth) {
        return reservationService.confirm(auth.getName(), id, req.method());
    }

    /** UC-07 내 예약(S06) */
    @GetMapping("/api/reservations/me")
    public List<ReservationDto> mine(Authentication auth) {
        return studentService.getMyReservations(auth.getName());
    }

    /** UC-08 식권 정보(S05) */
    @GetMapping("/api/reservations/{id}/ticket")
    public TicketDto ticket(@PathVariable Integer id, Authentication auth) {
        return studentService.getTicket(auth.getName(), id);
    }

    /** UC-08 QR 이미지(S05, image/png) */
    @GetMapping(value = "/api/reservations/{id}/ticket/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ticketQr(@PathVariable Integer id, Authentication auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(studentService.getTicketQr(auth.getName(), id));
    }

    /** UC-09~10 예약 취소와 환불(S06) */
    @PostMapping("/api/reservations/{id}/cancel")
    public ReservationDto cancel(@PathVariable Integer id, Authentication auth) {
        studentService.cancelReservation(auth.getName(), id);
        return studentService.getReservation(auth.getName(), id);
    }
}
