package kr.suhsaechan.secbrief.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity 생성·수정 시각 자동 기록 켜기
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
