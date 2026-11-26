# secbrief 서버 (Spring Boot)

공통 규칙은 루트 [CLAUDE.md](../CLAUDE.md)를 따른다. 이 문서는 서버 전용 규칙이다.

## 스택

Java 21, Spring Boot 3.5.16, Gradle 멀티모듈, PostgreSQL, Flyway. 패키지 `kr.suhsaechan.secbrief`.
의존성 저장소는 mavenCentral 만 쓴다.

## 모듈 (의존은 아래에서 위로만)

| 모듈 | 역할 |
|---|---|
| `SB-Common` | ErrorCode, CustomException, 표준 응답(ApiResponse, ErrorResponse), BaseEntity |
| `SB-Domain-Member` | 푸시 기기(토큰, 켠 알림 종류), 푸시 발송 규약과 Expo 구현 |
| `SB-Domain-Advisory` | 수집한 보안 공지·뉴스, 취약점, 관심 제품 |
| `SB-Application` | 여러 도메인을 묶는 유스케이스 (수집, AI 제목 분석·요약, 임베딩, 긴급 알림, 이미지 저장) |
| `SB-Web` | 시작점, Controller, 전역 예외 처리, 스케줄러, Flyway 마이그레이션 |
| `SB-Tool` | 로컬 전용 도구 (요약 하네스 평가, 밀린 요약 적재). 서버 이미지에 들어가지 않음 |

## 규칙

- 예외는 `CustomException(ErrorCode)` 하나만 던진다. 도메인별 예외 클래스를 만들지 않는다.
- 성공 응답은 `ApiResponse.of(data)`, 실패 응답은 전역 예외 처리가 `ErrorResponse`로 만든다.
- 스키마 변경은 Flyway 파일로만 한다 (`ddl-auto: validate`). 파일명은 `V{version.yml 버전}__설명.sql`.
- 주기 작업은 `SB-Web`의 `web.scheduler` 패키지에 둔다.
- 비밀값은 환경변수로만 받는다. `application-dev.yml`, `application-prod.yml`은 커밋하지 않는다.
- 로그는 영어로 쓰고 기능 접두사를 붙인다 (`[advisory collect] failed 502 : ...`). 외부에서 들어온 값은 정리한 뒤 넣는다.

- 기사 요약의 작성 규칙은 `SB-Domain-Advisory/src/main/resources/summary-harness/*.md` 가 단일 원본이다 (공통 규칙 + 글 종류별 안내 + 고쳐 쓰기).
  고칠 때는 [docs/summary-harness](../docs/summary-harness/README.md) 의 평가 순서로 기준선과 비교한다. 요약 1건의 제공자 호출은 시도마다 최대 2회다.

## 외부 연동 설정 (환경변수 또는 운영 설정 파일)

값이 비어 있으면 그 기능만 건너뛰고 서버 기동·수집·조회는 그대로 동작한다. 값은 커밋·로그에 남기지 않는다.
비밀값은 운영 설정 파일(`application-prod.yml`) 한 곳의 `secbrief.*` 키로만 넣는다. 파일 전체가 Secret `APPLICATION_PROD_YML` 이고, 배포 워크플로는 값을 따로 환경변수로 넘기지 않는다.

| 기능 | 설정 키 (`secbrief.`) | 환경변수 | 비어 있을 때 |
|---|---|---|---|
| 수집 | `collect.enabled` | `COLLECT_ENABLED` | 켜짐 (`false` 면 수집 안 함) |
| AI 처리 (제목 분석) | `ai.enabled` | `AI_ENABLED` | 켜짐 (`false` 면 AI 처리 안 함, 원문 제목·general 로 표시) |
| 설정 저장소 비밀값 암호화 | `secret.master-key` (임의의 긴 문자열) | `SECBRIEF_SECRET_MASTER_KEY` | 서버는 뜨고 비밀 설정(관리자 화면의 AI 키) 저장만 거부, 저장해 둔 비밀값은 읽지 못함 |
| AI 제공자 선택 | `ai.provider` (`anthropic`, `openai`, `google`, `ollama`, 쉼표로 우선순위) | `AI_PROVIDER` | `anthropic` (모르는 값이면 경고 로그만 남기고 AI 처리 건너뜀). 설정 저장소 `AI_PROVIDER_PRIORITY` 가 있으면 그 값이 우선 |
| AI (Anthropic) | `ai.anthropic-api-key`(쉼표로 여러 개), `ai.model`(기본 `claude-haiku-5-5`, 환경변수 없음) | `ANTHROPIC_API_KEY` | AI 처리 건너뜀 (원문 제목으로 표시). 관리자 화면에 켜진 키가 있으면 그 키를 쓰고, 없을 때만 이 값을 씀 |
| AI (OpenAI) | `ai.openai.base-url`(기본 `https://api.openai.com`, 환경변수 없음). 키·모델은 관리자 화면에서만 넣음 (설정 저장소 `OPENAI_API_KEYS`, `OPENAI_MODEL`) | 없음 | 켜진 키가 없으면 이 제공자를 건너뜀 (다음 순위 제공자로) |
| AI (Google Gemini) | `ai.google.base-url`(기본 `https://generativelanguage.googleapis.com`, 환경변수 없음). 키·모델은 관리자 화면에서만 넣음 (설정 저장소 `GOOGLE_API_KEYS`, `GOOGLE_MODEL`) | 없음 | 켜진 키가 없으면 이 제공자를 건너뜀 (다음 순위 제공자로) |
| AI (Ollama) | `ai.ollama.base-url`, `ai.ollama.model`, `ai.ollama.api-key`(선택) | `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_API_KEY` | AI 처리 건너뜀 |
| 기사 요약 (앱 안 상세) | `summary.enabled`, `summary.daily-limit`(기본 300) + `ai.*` 제공자·모델 재사용 | `SUMMARY_ENABLED`, `SUMMARY_DAILY_LIMIT` | 켜짐 (AI 제공자 미설정이면 요약 건너뜀, 상세는 제목·사실 값·원문 링크로 열림) |
| 긴급 알림 | `push.enabled`, `push.expo.access-token`(선택), `push.expo.experience-id`(선택), `push.expo.base-url`(기본 `https://exp.host`, 환경변수 없음) | `PUSH_ENABLED`, `EXPO_ACCESS_TOKEN`, `EXPO_EXPERIENCE_ID` | 켜짐 (등록 기기가 없으면 보내지 않음) |
| 기사 이미지 처리 | `image.enabled` | `IMAGE_ENABLED` | 켜짐 (`false` 면 내려받기·저장 안 함, 저장소 미설정이면 건너뜀) |
| 기사 이미지 저장소 (MinIO) | `image-storage.endpoint`, `image-storage.access-key`, `image-storage.secret-key`, `image-storage.bucket`(기본 `secbrief`) | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | 이미지 처리 건너뜀 (앱은 기본 그림 표시) |
| 제목 임베딩·의미 검색 (Ollama) | `embedding.enabled`, `embedding.ollama.model`, `embedding.minimum-similarity`(기본 0.5, 환경변수 없음) + `ai.ollama.base-url`·`ai.ollama.api-key` 재사용 | `EMBEDDING_ENABLED`, `OLLAMA_EMBEDDING_MODEL` | 임베딩 건너뜀 (검색은 글자 일치로 동작) |

주기·처리량 키는 환경변수가 없어 설정 파일로만 바꾼다. 기본값의 원본은 `SB-Web/src/main/resources/application.yml` 이다.

| 기능 | 설정 키 (`secbrief.`) | 기본값 (표 순서대로) |
|---|---|---|
| 수집 | `collect.tick-milliseconds`, `collect.initial-delay-milliseconds` | 60초, 30초 (실제 수집 주기는 출처별 `interval_minutes`) |
| AI 처리 | `ai.batch-size`, `ai.request-batch-size`, `ai.tick-milliseconds`, `ai.initial-delay-milliseconds` | 20건, 10건, 5분, 90초 |
| 기사 요약 | `summary.batch-size`, `summary.tick-budget-seconds`, `summary.tick-milliseconds`, `summary.initial-delay-milliseconds` | 10건, 90초, 3분, 210초 |
| 기사 이미지 처리 | `image.batch-size`, `image.tick-budget-seconds`, `image.tick-milliseconds`, `image.initial-delay-milliseconds` | 20건, 60초, 2분, 150초 |
| 제목 임베딩 | `embedding.batch-size`, `embedding.request-batch-size`, `embedding.tick-milliseconds`, `embedding.initial-delay-milliseconds` | 100건, 25건, 5분, 210초 |
| 긴급 알림 | `push.alert-tick-milliseconds`, `push.alert-initial-delay-milliseconds`, `push.receipt-tick-milliseconds`, `push.receipt-initial-delay-milliseconds` | 5분, 2분, 15분, 3분 |

## 설정 저장소 (관리자 화면에서 바꾸는 값)

`ConfigKey`(SB-Common `common.config`)가 단일 원본이다. 읽는 순서는 **설정 저장소(`system_config`) > 운영 설정(`application*.yml`) > `ConfigKey` 기본값**이고, 바꾼 값은 30초 안에 반영된다.

| ConfigKey | 같은 값의 운영 설정 키 (`secbrief.`) | 기본값 |
|---|---|---|
| `AI_PROVIDER_PRIORITY` | `ai.provider` | `anthropic` |
| `ANTHROPIC_API_KEYS` (비밀, AI 키 화면 전용) | 없음 (`ai.anthropic-api-key` 는 켜진 키가 없을 때의 fallback) | 없음 |
| `OPENAI_API_KEYS`, `GOOGLE_API_KEYS` (비밀, AI 키 화면 전용) | 없음 (운영 설정 fallback 없음) | 없음 |
| `OPENAI_MODEL`, `GOOGLE_MODEL` | 없음 (Anthropic 모델은 `ai.model` 로 고정이라 설정 저장소에 없음) | `gpt-6-luna`, `gemini-3.5-flash-lite` |
| `OPENAI_REASONING_EFFORT` (`none`·`minimal`·`low`·`medium`·`high`), `GOOGLE_THINKING_LEVEL` (`minimal`·`low`·`medium`·`high`) | 없음 | `none`, `minimal` (모델마다 받는 값이 다름: 모델을 바꾼 뒤 400 이면 `low`) |
| `AI_TITLE_BATCH_SIZE`, `AI_REQUEST_BATCH_SIZE` | `ai.batch-size`, `ai.request-batch-size` | 20, 10 |
| `SUMMARY_BATCH_SIZE`, `SUMMARY_DAILY_LIMIT` | `summary.batch-size`, `summary.daily-limit` | 10, 300 |
| `AI_DAILY_BUDGET_USD` | 없음 | -1 (예산 확인 끔) |
| `PRICE_ANTHROPIC_INPUT_PER_1M`, `PRICE_ANTHROPIC_OUTPUT_PER_1M` | 없음 | 0.10, 0.50 |
| `PRICE_OPENAI_INPUT_PER_1M`, `PRICE_OPENAI_OUTPUT_PER_1M` | 없음 | 0.10, 0.50 (기본 모델 `gpt-6-luna` 요금, 모델을 바꾸면 같이 바꿈) |
| `PRICE_GOOGLE_INPUT_PER_1M`, `PRICE_GOOGLE_OUTPUT_PER_1M` | 없음 | 0.30, 2.50 (기본 모델 `gemini-3.5-flash-lite` 요금, 생각 토큰도 출력 단가) |
| `URGENT_ALERT_ENABLED` | 없음 (`push.enabled=false` 면 이 값과 무관하게 꺼짐) | true |

- 비밀 종류는 AES-256-GCM 으로 암호화해 저장한다 (`enc:v1:` + Base64(nonce ‖ 암호문 ‖ tag), AAD = 설정 키 이름). 값은 화면·이력·감사 기록·로그에 남기지 않는다.
- 키는 제공자(Anthropic·OpenAI·Google)별 목록의 우선순위대로 쓰고 401·402·403·429·529·5xx·연결 실패·크레딧 부족이면 다음 키로 넘어간다 (실패한 키는 5분 뒤로 미룸). 호출마다 `ai_call_log` 에 제공자·모델·키 별명과 함께 1행이 남는다. 이 순회·기록은 `AiApiKeyCallRunner` 한 곳이 맡는다.
- 관리자 화면 주소는 제공자 코드를 담는다 (`/admin/ai-keys/{providerCode}/...`). OpenAI 는 Responses API(`/v1/responses`), Google 은 Interactions API(`/v1beta/interactions`)를 부르고, 출력 스키마는 제공자별 변환기(`advisory.ai.schema`)가 각 제공자가 받는 부분집합으로 바꾼다.

새 제공자를 붙일 때: AI 는 `StructuredCompletionClient` 구현 1개 (제목 분석·기사 요약이 같이 씀, 키 목록으로 부르는 제공자는 `KeyedAiProvider` 값 1개 + `AiApiKeyCallRunner` 사용), 푸시는 `PushSender` 구현 1개, 수집 방식은 `ArticleCollector` 구현 1개 + `FeedFormat` 값, 이미지 저장소는 `ArticleImageStorage` 구현 1개, 임베딩은 `TextEmbeddingProvider` 구현 1개.

## 검증

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew compileJava                 # 컴파일 확인
./gradlew test --tests '*Xxx*'        # 바꾼 코드를 겨누는 테스트만
```

서버 기동에는 PostgreSQL 이 필요하다 (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`).

## 배포 (Synology)

| 항목 | 값 |
|---|---|
| 워크플로 | `.github/workflows/PROJECT-SPRING-SYNOLOGY-SECBRIEF-CICD.yaml` (`deploy` 푸시, `server/**` 변경 시) |
| 이미지 | Docker Hub `{DOCKERHUB_USERNAME}/secbrief-back` (태그: 브랜치, 버전, `sha-짧은해시`) |
| 컨테이너 | `secbrief-back-blue` / `secbrief-back-green` (Traefik Blue-Green, 헬스 `/actuator/health`) |
| 도메인 | `api.secbrief.suhsaechan.kr` > DSM 리버스 프록시 > Traefik 8079 |
| 운영 설정 | Secret `APPLICATION_PROD_YML` > NAS `/volume1/projects/secbrief/backend/config/application-prod.yml` > `/app/config` |
| DB | NAS 공용 PostgreSQL, DB `secbrief` + NAS 공용 계정 (프로젝트별 계정 없음, 접속 정보는 운영 설정에만) |
| 롤백 | 워크플로 수동 실행 `rollback_image_tag` 에 이전 태그 입력 |
