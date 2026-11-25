package kr.ac.cbnu.cafeteria.repository;

import kr.ac.cbnu.cafeteria.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {
}
