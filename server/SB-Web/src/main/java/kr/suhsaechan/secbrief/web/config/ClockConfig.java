package kr.suhsaechan.secbrief.web.config;

import java.time.Clock;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시계 빈: 수집·조회·NVD 간격이 같은 시계를 씀 (테스트는 고정 시계로 교체)
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(SeoulCalendar.SEOUL);
    }
}
