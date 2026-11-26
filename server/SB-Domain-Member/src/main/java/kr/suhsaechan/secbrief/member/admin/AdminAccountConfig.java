package kr.suhsaechan.secbrief.member.admin;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 초기 관리자 계정 설정 묶음 등록
 */
@Configuration
@EnableConfigurationProperties(AdminAccountProperties.class)
public class AdminAccountConfig {
}
