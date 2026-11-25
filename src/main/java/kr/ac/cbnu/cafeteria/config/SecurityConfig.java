package kr.ac.cbnu.cafeteria.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.cbnu.cafeteria.web.dto.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;

/*=====================================================================
 파일명   : SecurityConfig.java
 기능     : 세션 인증과 역할 기반 접근 통제(NFR-02, EIR-SW-04, EIR-NW-04).
            /api/admin/** 은 ADMIN, 그 밖의 학생 API는 STUDENT만 허용한다.
            상태를 바꾸는 요청에는 CSRF 토큰(X-XSRF-TOKEN 헤더)을 요구한다.
=====================================================================*/
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);                   // 비용 계수 10(EIR-SW-04)
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper,
                                           SecurityContextRepository contextRepository) throws Exception {
        // 매 요청마다 토큰을 읽어 XSRF-TOKEN 쿠키가 항상 내려가도록 한다(화면 스크립트가 헤더에 담아 보냄).
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .securityContext(ctx -> ctx.securityContextRepository(contextRepository))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/me").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/**").hasRole("STUDENT")
                .anyRequest().permitAll())                       // 정적 화면 파일
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeError(res, objectMapper,
                        HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다"))
                .accessDeniedHandler((req, res, e) -> writeError(res, objectMapper,
                        HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다")))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());
        return http.build();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, int status,
                                   String code, String message) throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getWriter(), new ApiError(code, message));
    }
}
