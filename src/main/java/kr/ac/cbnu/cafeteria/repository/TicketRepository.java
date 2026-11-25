package kr.ac.cbnu.cafeteria.repository;

import kr.ac.cbnu.cafeteria.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Integer> {

    /** SQL> SELECT * FROM tickets WHERE qr_code = :qrCode */
    Optional<Ticket> findByQrCode(String qrCode);

    /** SQL> SELECT * FROM tickets WHERE reservation_id = :reservationId */
    @Query("select t from Ticket t where t.reservation.reservationId = :reservationId")
    Optional<Ticket> findByReservationId(@Param("reservationId") Integer reservationId);

    boolean existsByQrCode(String qrCode);
}
