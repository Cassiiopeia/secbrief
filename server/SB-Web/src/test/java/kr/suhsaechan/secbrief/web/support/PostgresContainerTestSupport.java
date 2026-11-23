package kr.suhsaechan.secbrief.web.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DB 통합 테스트 공통 부모: 실제 PostgreSQL 컨테이너 1개를 테스트 클래스 단위로 공유
 * 도커가 없으면 테스트를 건너뜀 (건너뛴 DB 테스트는 통과로 세지 않음)
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresContainerTestSupport {

    // @ServiceConnection: 컨테이너 접속 정보로 spring.datasource.* 자동 대체
    @Container
    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
