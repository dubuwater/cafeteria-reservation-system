package kr.ac.cbnu.cafeteria.service;

import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.Menu;
import kr.ac.cbnu.cafeteria.domain.Payment;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import kr.ac.cbnu.cafeteria.domain.Ticket;
import kr.ac.cbnu.cafeteria.domain.TimeSlot;
import kr.ac.cbnu.cafeteria.payment.PaymentGateway;
import kr.ac.cbnu.cafeteria.repository.ReservationRepository;
import kr.ac.cbnu.cafeteria.repository.TicketRepository;
import kr.ac.cbnu.cafeteria.web.dto.ConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/*=====================================================================
 파일명   : ReservationService.java
 기능     : 예약 확정과 취소 트랜잭션(설계서 4.6.2, 4.6.4). 재고·정원 검사 전에 행을 잠가
            마지막 재고를 두 요청이 동시에 확보하지 못하게 하고(NFR-04), 이미 반영된 요청은
            다시 반영하지 않는다(NFR-05). 잠금 순서: 예약 → 메뉴 → 시간대.
=====================================================================*/
@Service
public class ReservationService {

    private static final int QR_RETRY = 3;

    private final ReservationRepository reservations;
    private final TicketRepository tickets;
    private final TimeSlotService timeSlotService;
    private final PaymentGateway paymentGateway;
    private final LockSupport locks;
    private final Clock clock;

    public ReservationService(ReservationRepository reservations, TicketRepository tickets,
                              TimeSlotService timeSlotService, PaymentGateway paymentGateway,
                              LockSupport locks, Clock clock) {
        this.reservations = reservations;
        this.tickets = tickets;
        this.timeSlotService = timeSlotService;
        this.paymentGateway = paymentGateway;
        this.locks = locks;
        this.clock = clock;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Reservation::confirm(String method)
    2) 매개변수: studentId = 세션의 학생; reservationId = 결제대기 예약; method = 'CARD' 또는 'SIMPLE_PAY'
    3) 기능: 재고·정원 재검사 → 모의 결제 → 재고 차감 → 예약완료 → 식권 발급을 한 트랜잭션으로 처리한다
             (FR-06~07, DR-03, DR-06, NFR-04~05).
    4) 반환값: 확정 결과. 이미 확정된 예약이면 아무것도 다시 반영하지 않고 같은 결과를 돌려준다.
               실패 시 예외 → 전체 롤백(모든 값이 호출 전과 같다)
    ---------------------------------------------------------------------*/
    @Transactional
    public ConfirmResponse confirm(String studentId, Integer reservationId, String method) {
        if (method == null || !Payment.METHODS.contains(method)) {
            throw BusinessException.invalid("INVALID_METHOD", "결제 수단을 선택하세요");
        }
        // 1. 예약 행을 잠그고 상태를 확인한다. SQL> SELECT * FROM reservations WHERE … FOR UPDATE
        Reservation r = lockOwned(studentId, reservationId);
        if (r.getStatus() == ReservationStatus.RESERVED) {
            return ConfirmResponse.from(r);                      // 같은 요청의 재전송(NFR-05)
        }
        if (r.getStatus() != ReservationStatus.PENDING) {
            throw BusinessException.conflict("BAD_STATE", "확정할 수 없는 예약입니다");
        }
        // 2. 메뉴 → 시간대 순서로 잠근 뒤 재고와 정원을 다시 검사한다.
        Menu menu = locks.lock(r.getMenu());
        TimeSlot slot = locks.lock(r.getSlot());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!menu.getSaleDate().equals(now.toLocalDate()) || !r.getCreatedAt().toLocalDate().equals(now.toLocalDate())) {
            throw BusinessException.conflict("NOT_TODAY", "오늘의 메뉴에서 다시 예약하세요");
        }
        if (menu.getMealType() != slot.getMealType()) {
            throw BusinessException.conflict("SLOT_MISMATCH", "메뉴의 식사 구분이 변경되었습니다. 수령 시간대를 다시 선택하세요");
        }
        if (!menu.isAvailable(r.getQuantity())) {
            throw BusinessException.conflict("STOCK_SHORTAGE", "남은 수량이 부족합니다");
        }
        if (!timeSlotService.hasCapacity(slot, menu.getSaleDate())) {
            throw BusinessException.conflict("SLOT_FULL", "선택한 시간대가 마감되었습니다");
        }
        // 3~4. 확정 당시 단가로 총액을 계산하고(DR-11) 모의 결제를 한다.
        //      재검사 뒤에 결제하므로 재고 부족 시 환불할 결제가 생기지 않는다.
        Payment payment = Payment.pay(r, r.calculateTotal(), method, paymentGateway, now);
        if (payment == null) {
            throw BusinessException.conflict("PAY_FAILED", "결제에 실패했습니다");   // 예약은 PENDING 유지
        }
        // 5~6. 식권을 발급하고, 재고 차감·상태 변경과 함께 반영한다(예약 1건당 식권 1개).
        Ticket ticket = new Ticket(r, generateQR(), now);
        r.confirm(payment, ticket);
        reservations.flush();                                    // 식권 번호 등 생성 값을 응답에 포함
        return ConfirmResponse.from(r);
    }

    /*---------------------------------------------------------------------
    1) 메소드명: boolean Reservation::cancel()
    2) 매개변수: studentId = 세션의 학생(소유자 확인, 설계서 4.2.4); reservationId = 취소할 예약
    3) 기능: 환불, 재고 복구, 식권 무효화, 상태 변경을 한 트랜잭션으로 처리한다(FR-11, DR-07, NFR-04).
             동시에 들어온 두 번째 요청은 잠금을 기다린 뒤 CANCELED 상태를 보고 거절된다.
    4) 반환값: 취소 성공 시 true. 실패 시 예외
    ---------------------------------------------------------------------*/
    @Transactional
    public boolean cancel(String studentId, Integer reservationId) {
        // 1. 예약 행을 잠근다.
        Reservation r = lockOwned(studentId, reservationId);
        if (r.getStatus() == ReservationStatus.CANCELED) {
            throw BusinessException.conflict("ALREADY_CANCELED", "이미 취소된 예약입니다");
        }
        if (r.getStatus() == ReservationStatus.PICKED_UP) {
            throw BusinessException.conflict("ALREADY_PICKED_UP", "수령이 완료된 예약은 취소할 수 없습니다");
        }
        // 2. 서버 시각으로 기한과 상태를 확인한다(DR-05).
        if (!r.canCancel(LocalDateTime.now(clock))) {
            if (r.getStatus() != ReservationStatus.RESERVED) {
                throw BusinessException.conflict("BAD_STATE", "취소할 수 없는 예약입니다");
            }
            throw BusinessException.conflict("CANCEL_DEADLINE", "취소 가능 시간이 지났습니다");
        }
        // 3. 메뉴 행을 잠근다(예약 → 메뉴 순서).
        locks.lock(r.getMenu());
        // 4. 전액 환불한다. 이미 환불된 결제는 false가 되어 이중 환불을 막는다.
        if (!r.getPayment().refund(paymentGateway)) {
            throw BusinessException.conflict("REFUND_FAILED", "환불에 실패했습니다");
        }
        // 5. 재고 복구, 식권 무효화, 상태 변경. 시간대 집계에서는 상태 변경으로 자동 제외된다.
        r.markCanceled();
        return true;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: String Ticket::generateQR()
    3) 기능: 추측할 수 없는 UUID v4 문자열을 만들고 qr_code UNIQUE 충돌 시 다시 만든다(최대 3회).
             예약 번호 등 개인 정보는 넣지 않는다(FR-07). 설계서 4.8.1
    4) 반환값: 36자 QR 문자열. 실패하면 예외 → confirm() 전체 롤백
    ---------------------------------------------------------------------*/
    String generateQR() {
        for (int i = 0; i < QR_RETRY; i++) {
            String code = UUID.randomUUID().toString();
            if (!tickets.existsByQrCode(code)) {
                return code;
            }
        }
        throw BusinessException.conflict("QR_FAILED", "식권 발급에 실패했습니다");
    }

    private Reservation lockOwned(String studentId, Integer reservationId) {
        Reservation r = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> BusinessException.notFound("NO_RESERVATION", "예약을 찾을 수 없습니다"));
        // 본인 예약이 아니면 거절한다(서버 소유권 검사, NFR-02).
        if (!r.isOwnedBy(studentId)) {
            throw BusinessException.forbidden();
        }
        return r;
    }
}
