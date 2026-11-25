package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** 학생(Student) — User 상속(설계서 3.2.2절). 메소드 알고리즘은 StudentService에 있다. */
@Entity
@DiscriminatorValue("STUDENT")
public class Student extends User {

    @Column(name = "department", length = 30)
    private String department;

    protected Student() {
    }

    public Student(String userId, String passwordHash, String name, String department) {
        super(userId, passwordHash, name);
        this.department = department;
    }

    @Override
    public Role getRole() {
        return Role.STUDENT;
    }

    public String getDepartment() {
        return department;
    }
}
