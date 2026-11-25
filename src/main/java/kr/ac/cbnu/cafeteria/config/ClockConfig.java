package kr.ac.cbnu.cafeteria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 서버 기준 시계(설계서 4.0절). 현재 일시는 이 Clock에서만 얻고 클라이언트 시각은 쓰지 않는다(DR-05).
 * 테스트에서는 고정 시계로 바꿔 취소 기한 경계값을 검증한다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:Asia/Seoul}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
