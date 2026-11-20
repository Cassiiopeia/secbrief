package kr.suhsaechan.secbrief.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업(@Scheduled) 켜기: 작업 클래스는 web.scheduler 패키지에 둠
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
