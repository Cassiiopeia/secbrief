# secbrief 서버 (Spring Boot)

공통 규칙은 루트 [CLAUDE.md](../CLAUDE.md)를 따른다. 이 문서는 서버 전용 규칙이다.

## 스택

Java 21, Spring Boot 3.5.16, Gradle 멀티모듈, PostgreSQL, Flyway. 패키지 `kr.suhsaechan.secbrief`.
의존성 저장소는 mavenCentral 만 쓴다.

## 모듈 (의존은 아래에서 위로만)

| 모듈 | 역할 |
|---|---|
| `SB-Common` | ErrorCode, CustomException, 표준 응답(ApiResponse, ErrorResponse), BaseEntity |
| `SB-Domain-Member` | 회원, 푸시 토큰, 알림 설정 |
| `SB-Domain-Advisory` | 수집한 보안 공지·뉴스, 취약점, 관심 제품 |
| `SB-Application` | 여러 도메인을 묶는 유스케이스 (수집 > 정리 > 알림) |
| `SB-Web` | 시작점, Controller, 전역 예외 처리, 스케줄러, Flyway 마이그레이션 |

## 규칙

- 예외는 `CustomException(ErrorCode)` 하나만 던진다. 도메인별 예외 클래스를 만들지 않는다.
- 성공 응답은 `ApiResponse.of(data)`, 실패 응답은 전역 예외 처리가 `ErrorResponse`로 만든다.
- 스키마 변경은 Flyway 파일로만 한다 (`ddl-auto: validate`). 파일명은 `V{version.yml 버전}__설명.sql`.
- 주기 작업은 `SB-Web`의 `web.scheduler` 패키지에 둔다.
- 비밀값은 환경변수로만 받는다. `application-dev.yml`, `application-prod.yml`은 커밋하지 않는다.
- 로그는 영어로 쓰고 기능 접두사를 붙인다 (`[advisory collect] failed 502 : ...`). 외부에서 들어온 값은 정리한 뒤 넣는다.

## 검증

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew compileJava                 # 컴파일 확인
./gradlew test --tests '*Xxx*'        # 바꾼 코드를 겨누는 테스트만
```

서버 기동에는 PostgreSQL 이 필요하다 (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`).
