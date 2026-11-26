package kr.suhsaechan.secbrief.web.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DB 통합 테스트 공통 부모: 실제 PostgreSQL 컨테이너 1개를 테스트 JVM 전체에서 공유
 * 도커가 없으면 테스트를 건너뜀 (건너뛴 DB 테스트는 통과로 세지 않음)
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresContainerTestSupport {

    // @Container 를 쓰면 테스트 클래스마다 컨테이너가 꺼지는데, 스프링은 캐시한 컨텍스트(옛 포트)를 재사용해 두 번째 클래스부터 접속 실패
    // 그래서 한 번만 띄우고 JVM 종료 때 정리 (Testcontainers 가 정리 담당)
    // @ServiceConnection: 컨테이너 접속 정보로 spring.datasource.* 자동 대체
    @ServiceConnection
    // 접속 상한 300: 캐시한 컨텍스트마다 접속 풀(10개)을 들고 있어 기본 100 이면 컨텍스트가 늘 때 접속 거부
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        // 도커 없으면 시작하지 않음 (@Testcontainers 가 테스트를 건너뜀)
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
