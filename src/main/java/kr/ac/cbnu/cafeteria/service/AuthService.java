package kr.ac.cbnu.cafeteria.service;

import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.User;
import kr.ac.cbnu.cafeteria.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*=====================================================================
 파일명   : AuthService.java
 기능     : 로그인 계정 확인(설계서 4.1.1 User::login). 세션 생성은 AuthController가 한다.
 관련 요구 : FR-01, NFR-03
=====================================================================*/
@Service
public class AuthService {

    static final String LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /*---------------------------------------------------------------------
    1) 메소드명: User User::login(String id, String pw)
    2) 매개변수: id = 학번 또는 관리자 아이디; pw = 입력한 비밀번호(평문, 저장하지 않음)
    3) 기능: 계정을 조회하고 BCrypt로 비밀번호를 비교한다.
    4) 반환값: 확인된 사용자. 실패 시 401 예외(계정 유무와 관계없이 같은 메시지)
    ---------------------------------------------------------------------*/
    @Transactional(readOnly = true)
    public User login(String id, String pw) {
        // 1. 필수 입력을 검사한다.
        if (id == null || id.isBlank() || pw == null || pw.isEmpty()) {
            throw BusinessException.invalid("REQUIRED", "아이디와 비밀번호를 입력하세요");
        }
        // 2. 아이디로 계정을 조회한다. SQL> SELECT … FROM users WHERE user_id = :id
        User user = users.findById(id.trim()).orElse(null);
        // 3. 계정이 없거나 비밀번호가 다르면 같은 결과를 돌려 계정 존재 여부를 숨긴다.
        if (user == null || !passwordEncoder.matches(pw, user.getPasswordHash())) {
            throw BusinessException.unauthorized("LOGIN_FAILED", LOGIN_FAILED_MESSAGE);
        }
        return user;
    }
}
