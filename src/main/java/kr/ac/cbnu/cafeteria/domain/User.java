package kr.ac.cbnu.cafeteria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/*=====================================================================
 파일명   : User.java
 기능     : 사용자(User) 추상 클래스. 학생과 관리자가 공유하는 아이디, 비밀번호 해시,
            이름을 가진다. users 테이블 하나에 role 컬럼으로 구분해 저장한다(설계서 3.3절).
 관련 요구 : FR-01, FR-02, NFR-03
=====================================================================*/
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING, length = 10)
public abstract class User {

    @Id
    @Column(name = "user_id", length = 20)
    protected String userId;

    @Column(name = "password_hash", length = 60, nullable = false)
    protected String passwordHash;

    @Column(name = "name", length = 20, nullable = false)
    protected String name;

    protected User() {
    }

    protected User(String userId, String passwordHash, String name) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    /** 역할. 로그인 후 첫 화면과 접근 권한을 결정한다. */
    public abstract Role getRole();

    public String getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }
}
