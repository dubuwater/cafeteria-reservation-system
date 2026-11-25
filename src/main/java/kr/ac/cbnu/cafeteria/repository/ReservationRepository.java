package kr.ac.cbnu.cafeteria.repository;

import jakarta.persistence.LockModeType;
import kr.ac.cbnu.cafeteria.domain.Reservation;
import kr.ac.cbnu.cafeteria.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {

    /** SQL> SELECT * FROM reservations WHERE reservation_id = :id FOR UPDATE */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Integer id);

    /** 시간대 정원 집계(DR-04, 설계서 4.5.1): 해당 날짜·시간대의 확보 예약 건수 */
    @Query("select count(r) from Reservation r "
            + "where r.slot.slotId = :slotId and r.menu.saleDate = :date and r.status in :statuses")
    long countBySlotAndDate(@Param("slotId") Integer slotId, @Param("date") LocalDate date,
                            @Param("statuses") Collection<ReservationStatus> statuses);

    /** 날짜별 시간대 집계: [slotId, 예약 건수, 수령완료 건수] (설계서 4.3.3) */
    @Query("select r.slot.slotId, count(r), sum(case when r.status = :picked then 1 else 0 end) "
            + "from Reservation r "
            + "where r.menu.saleDate = :date and r.status in :statuses group by r.slot.slotId")
    List<Object[]> aggregateBySlot(@Param("date") LocalDate date,
                                   @Param("statuses") Collection<ReservationStatus> statuses,
                                   @Param("picked") ReservationStatus picked);

    /** 확보 수량(DR-10, 설계서 4.3.2): 메뉴의 예약완료·수령완료 수량 합계 */
    @Query("select coalesce(sum(r.quantity), 0) from Reservation r "
            + "where r.menu.menuId = :menuId and r.status in :statuses")
    long sumQuantityByMenu(@Param("menuId") Integer menuId,
                           @Param("statuses") Collection<ReservationStatus> statuses);

    /** 내 예약(FR-08, 설계서 4.2.3): 결제대기 제외, 최신순 */
    @Query("select r from Reservation r join fetch r.menu join fetch r.slot "
            + "where r.student.userId = :studentId and r.status <> :excluded "
            + "order by r.createdAt desc, r.reservationId desc")
    List<Reservation> findMine(@Param("studentId") String studentId,
                               @Param("excluded") ReservationStatus excluded);

    /** 예약 현황 상세(FR-14, A02) */
    @Query("select r from Reservation r join fetch r.student join fetch r.menu "
            + "where r.slot.slotId = :slotId and r.menu.saleDate = :date and r.status <> :excluded "
            + "order by r.reservationId")
    List<Reservation> findForSlot(@Param("slotId") Integer slotId, @Param("date") LocalDate date,
                                  @Param("excluded") ReservationStatus excluded);
}
