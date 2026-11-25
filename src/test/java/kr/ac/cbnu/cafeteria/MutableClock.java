package kr.ac.cbnu.cafeteria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트용 시계. 취소 기한 경계값처럼 서버 시각에 따라 달라지는 규칙을 검증한다. */
public class MutableClock extends Clock {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private Instant instant;

    public MutableClock(LocalDateTime start) {
        set(start);
    }

    public void set(LocalDateTime time) {
        this.instant = time.atZone(SEOUL).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return SEOUL;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
