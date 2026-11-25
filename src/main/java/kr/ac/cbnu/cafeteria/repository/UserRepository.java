package kr.ac.cbnu.cafeteria.repository;

import kr.ac.cbnu.cafeteria.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
