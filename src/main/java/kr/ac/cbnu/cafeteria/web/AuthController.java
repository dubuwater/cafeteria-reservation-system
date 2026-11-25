package kr.ac.cbnu.cafeteria.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.ac.cbnu.cafeteria.domain.BusinessException;
import kr.ac.cbnu.cafeteria.domain.User;
import kr.ac.cbnu.cafeteria.repository.UserRepository;
import kr.ac.cbnu.cafeteria.service.AuthService;
import kr.ac.cbnu.cafeteria.web.dto.LoginRequest;
import kr.ac.cbnu.cafeteria.web.dto.UserDto;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 로그인·로그아웃(UC-01~02). 로그아웃은 SecurityConfig의 logout 설정이 처리한다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;
    private final SecurityContextRepository contextRepository;
    private final SessionRegistry sessionRegistry;

    public AuthController(AuthService authService, UserRepository users, SecurityContextRepository contextRepository,
                          SessionRegistry sessionRegistry) {
        this.authService = authService;
        this.users = users;
        this.contextRepository = contextRepository;
        this.sessionRegistry = sessionRegistry;
    }

    /** POST /api/auth/login — 설계서 4.1.1의 4단계(세션 생성) */
    @PostMapping("/login")
    public UserDto login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        User user = authService.login(req.id(), req.pw());
        HttpSession current = request.getSession(true);
        // 나중 로그인 우선: 같은 계정으로 로그인된 다른 기기의 세션을 만료시킨다.
        for (SessionInformation other : sessionRegistry.getAllSessions(user.getUserId(), false)) {
            if (!other.getSessionId().equals(current.getId())) {
                other.expireNow();
            }
        }
        // 세션 고정 공격을 막기 위해 세션 아이디를 새로 발급한다.
        request.changeSessionId();
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(user.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        sessionRegistry.registerNewSession(request.getSession().getId(), user.getUserId());
        return UserDto.from(user);                               // 첫 화면은 화면이 role로 결정(학생 S02, 관리자 A01)
    }

    /** GET /api/auth/me — 화면이 로그인 상태와 역할을 확인할 때 쓴다. */
    @GetMapping("/me")
    public UserDto me(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw BusinessException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다");
        }
        return users.findById(auth.getName()).map(UserDto::from)
                .orElseThrow(() -> BusinessException.unauthorized("UNAUTHORIZED", "로그인이 필요합니다"));
    }
}
