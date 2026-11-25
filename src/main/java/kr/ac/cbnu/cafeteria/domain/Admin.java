package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/** 관리자(Admin) — User 상속. 식당이 1곳이므로 고유 속성은 없다(설계서 3.2.3절). */
@Entity
@DiscriminatorValue("ADMIN")
public class Admin extends User {

    protected Admin() {
    }

    public Admin(String userId, String passwordHash, String name) {
        super(userId, passwordHash, name);
    }

    @Override
    public Role getRole() {
        return Role.ADMIN;
    }
}
