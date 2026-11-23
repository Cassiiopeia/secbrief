# 실제 콘텐츠 구축 (Play 재제출) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 서버가 공개 보안 출처 7곳을 주기 수집해 저장하고 Claude Haiku 로 제목 분류·해외 제목 번역을 붙이며, 앱 5개 탭이 그 실데이터(게시자·게시일·원문 링크 포함)로 작동하게 만든 뒤 새 빌드로 Play 비공개 테스트를 다시 제출한다.

**Architecture:** 수집은 Spring 서버 안 스케줄러(1분 틱 + 출처별 주기 판정)가 출처 형식별 수집기(RSS / CISA KEV JSON / NVD API 2.0 / GitHub 보안 권고 API)를 불러 정규화 > 긴급 판정 > 중복 제거·CVE 병합 > 저장(주제 `GENERAL`, `ai_status=PENDING`) 순으로 처리하고 `collect_run`에 남긴다. 별도 5분 주기 AI 스케줄러가 PENDING 항목을 작은 묶음으로 꺼내 인터페이스 뒤의 Anthropic 분석기로 주제·한국어 제목을 받아 검증 후 반영한다(실패 3회면 FAILED). 읽기 API 4개가 `ApiResponse`로 내려 주고, 앱은 `data/sources`의 fetch 클라이언트 + TanStack Query 훅으로 탭마다 조회해 원문을 인앱 브라우저로 연다.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Data JPA(Specification + FluentQuery + `Limit`), Flyway, PostgreSQL, Rome 2.1.0(RSS), Spring `RestClient`, 공식 Anthropic Java SDK(`com.anthropic:anthropic-java`), JUnit 5 + Mockito + Testcontainers(PostgreSQL) / Expo SDK 58, Expo Router, TanStack Query 5, expo-web-browser(설치됨 `~58.1.0`), expo-constants, Jest(jest-expo)

**Spec:** `docs/superpowers/specs/2026-11-23-real-content-for-play-resubmission-design.md` (상위: `docs/superpowers/specs/2026-11-20-secbrief-foundation-design.md`)

**기준 브랜치:** `origin/develop` (로컬 작업 트리는 오래됨). 이슈 브랜치를 `/pro-init-worktree`로 `origin/develop`에서 만든다. 커밋은 `/pro-commit`, 건드린 경로만 명시해 스테이징(`git add -A` 금지).

## Global Constraints

- 서버: Java 21, Spring Boot 3.5.16, 패키지 `kr.suhsaechan.secbrief`, 의존성 저장소 mavenCentral 만.
- 스키마는 Flyway 파일로만 (`ddl-auto: validate`). 파일명 `V0.3.0__create_source_article_collect_run.sql` (version.yml `0.3.0`).
- 예외는 `CustomException(ErrorCode)` 하나만. 성공 응답 `ApiResponse.of(data)`, 실패는 `GlobalExceptionHandler`가 `ErrorResponse {errorCode, message}`.
- 주기 작업은 `SB-Web`의 `kr.suhsaechan.secbrief.web.scheduler`.
- 로그는 영어 + 기능 접두사 (`[article collect] failed 502 : ...`, `[article ai] ...`), 외부 값은 정리 후 기록. API 키는 어떤 로그에도 남기지 않는다.
- 공개 출처만 수집. 회사 내부 자료는 수집·예시·시험 데이터(fixture 포함) 어디에도 넣지 않는다.
- 기사 본문을 저장·표시하지 않는다 (제목·링크·게시자·날짜만). NVD 설명은 제목 대용으로 앞 120자만 쓴다. AI 입력도 제목만.
- 영향 버전·조치 버전을 만들거나 추정하지 않는다. AI 번역 결과에 원문에 없는 숫자열이 생기면 번역을 버린다.
- 모든 항목에 출처 링크(`originalUrl`)와 게시자(`publisherName`), 게시일(`publishedAt`).
- NVD API: 키 없이 30초당 5회 → 요청 간격 최소 6초. CVSS 9.0 이상만 저장, 점수는 v3.1 우선 없으면 v4.0, `cvss_version`에 함께 저장.
- GitHub API: 인증 없이 시간당 60회 → GHSA 는 2시간마다 1회 요청.
- AI: Claude Haiku 5.5 `claude-haiku-5-5`, 공식 Anthropic Java SDK, 구조화 출력(JSON 스키마) `{topic, translatedTitle?}`, 키는 환경변수 `ANTHROPIC_API_KEY`. SDK 클래스·메서드 이름은 추측하지 않고 SDK README·javadoc 으로 확인한다. 테스트는 실제 API 를 부르지 않는다(가짜 분석기).
- AI 처리 전에도 항목은 원문 제목으로 바로 보인다. AI 실패 시 주제 `general`, 번역 없음, 최대 3회 시도 후 FAILED.
- 긴급: CVSS 9.0 이상, KEV 등재, KISA 공지 제목에 `긴급` 중 하나. 오늘 탭 긴급 유지 72시간.
- 오늘·이번 주 기준: Asia/Seoul, `published_at` 기준, 주는 월요일 시작.
- 주제 8종: `vulnerability`, `ransomware`, `privacy`, `remoteAccess`, `database`, `supplyChain`, `policy`, `general` (서버 enum 이름 `VULNERABILITY`, `RANSOMWARE`, `PRIVACY`, `REMOTE_ACCESS`, `DATABASE`, `SUPPLY_CHAIN`, `POLICY`, `GENERAL`). 새 3종 커버는 기존 그림 재사용.
- 첫 수집 범위: RSS 피드 전체, KEV `dateAdded` 최근 30일, NVD 게시일 최근 7일, GHSA 게시일 최근 7일(스펙에 없어 NVD 와 맞춤).
- 앱: Expo SDK 58. 패키지 추가는 `npx expo install` 만. 앱은 우리 서버하고만 통신.
- 앱 화면: 색·글꼴·간격 숫자 직접 금지(토큰만), `SbText` 만, `@/design` 진입점만, `style` 객체 직접 금지.
- 화면 문구는 한국어 해요체·능동형·긍정형. 오류 화면엔 에러 코드 표시. 번역 항목은 한국어 제목 크게 + 원문 제목 작게 + `AI 번역` 표시.
- 문의 이메일은 스펙 §11 "문의 연락처" 값(스토어·지원 페이지와 같은 주소, 이 문서에선 `<SUPPORT_EMAIL>`로 표기 — 구현 시 스펙에서 그대로 옮긴다). 개인정보처리방침 `https://cassiiopeia.github.io/secbrief/privacy.html`, 이용약관 `https://cassiiopeia.github.io/secbrief/terms.html`, 지원 페이지 `https://cassiiopeia.github.io/secbrief/support.html`.
- 운영 API 주소 `https://api.secbrief.suhsaechan.kr` (빌드 워크플로의 `EXPO_PUBLIC_API_BASE_URL`).
- 이름: 약어 금지, boolean은 `is/has/can/should`, 파일 300줄 넘기지 않기, 주석은 간결한 한국어(WHY·단계 머리글).
- `git push`, `deploy` 배포, Play 제출, GitHub Secret 값 입력, 파일 삭제(스펙이 정한 샘플 소스 제외)는 사용자가 명시적으로 승인할 때만. API 키 값은 사용자가 준다(지어내지 않음).

## Review Focus

1. **같은 CVE가 KEV·GHSA 먼저, NVD 나중에 들어오는 순서** — 한 행으로 합쳐지고 원문 링크·게시자는 NVD로 바뀌며 `is_kev=true`는 유지되어야 한다. → Task 8 `mergesKevFirstThenNvdIntoSingleNvdRow`, `replacesGhsaLinkWhenNvdArrivesLater`.
2. **출처가 게시일을 안 주거나 미래 시각을 주는 피드** (KISA RSS 시간대 누락 등) — 수집 시각으로 대체하고 `isPublishedAtEstimated=true`, 미래 시각이 오늘·동향을 앞지르지 않아야 한다. → Task 3 `usesCollectedAtWhenPublishedAtMissing`, `clampsFuturePublishedAtToNow`.
3. **AI 번역이 버전 숫자를 바꾸거나 지어내는 경우** (`2.4.1` → `2.4.0`, "3개 제품" 추가) — 번역을 버리고 원문 제목만 보여야 한다. → Task 10 `dropsTranslationWithDigitsNotInOriginal`, `dropsTranslationWithChangedVersion`.
4. **AI 키가 없거나 Anthropic 이 계속 실패** — 항목은 원문 제목·`general` 로 계속 보이고, 같은 항목을 무한히 다시 부르지 않으며(3회 후 FAILED), 서버가 뜨는 데 지장이 없어야 한다. → Task 11 `skipsWhenApiKeyMissing`, `marksFailedAfterThirdFailure`, Task 13 `returnsPendingArticleWithOriginalTitle`.
5. **출처 하나가 계속 실패** — 그 출처만 `FAILED`로 기록되고 1분마다 두드리지 않으며(주기 판정은 마지막 시도 기준), 다른 출처와 기존 데이터 제공은 그대로여야 한다. → Task 3 `isCollectDueUsesLastAttemptNotLastSuccess`, Task 9 `recordsFailedRunAndKeepsLastCollectedAt`, `oneSourceFailureDoesNotStopOthers`.

---

## File Structure

### 서버 (`server/`)

| 파일 | 책임 |
|---|---|
| `build.gradle` (루트) | Testcontainers 테스트 의존성 공통 추가 |
| `SB-Domain-Advisory/build.gradle` | Rome, Anthropic Java SDK, `java-test-fixtures` |
| `SB-Web/src/main/resources/db/migration/V0.3.0__create_source_article_collect_run.sql` | 테이블 3개 + 출처 7곳 시드 |
| `SB-Web/src/main/resources/application.yml` | `secbrief.collect.*`, `secbrief.ai.*` 설정 |
| `SB-Common/.../exception/constant/ErrorCode.java` | `FEED_PARSE_ERROR`, `AI_API_ERROR`, `INVALID_CURSOR` 추가 |
| `SB-Domain-Advisory/.../advisory/constant/{ArticleKind,ArticleRegion,ArticleTopic,AiStatus,FeedFormat,CollectStatus,SourceCode}.java` | 값 종류 |
| `SB-Domain-Advisory/.../advisory/entity/{Source,Article,CollectRun}.java` | 엔티티 (AI 상태 전이 포함) |
| `SB-Domain-Advisory/.../advisory/repository/{Source,Article,CollectRun}Repository.java` | 저장소 |
| `SB-Domain-Advisory/.../advisory/policy/ArticleUrgencyPolicy.java` | 긴급 판정 |
| `SB-Domain-Advisory/.../advisory/policy/{ArticleDraft,ArticleNormalizer}.java` | 수집 원본 > 저장 초안 |
| `SB-Domain-Advisory/.../advisory/policy/{CollectSchedulePolicy,SeoulCalendar}.java` | 주기 판정·수집 시작 시점, 오늘·이번 주 시작 시각 |
| `SB-Domain-Advisory/.../advisory/collector/{ArticleCollector,CollectedArticle,FeedHttpClient}.java` | 수집기 계약·HTTP |
| `SB-Domain-Advisory/.../advisory/collector/{RssFeedParser,RssArticleCollector}.java` | RSS 4곳 |
| `SB-Domain-Advisory/.../advisory/collector/{KevCatalogParser,KevArticleCollector}.java` | CISA KEV |
| `SB-Domain-Advisory/.../advisory/collector/{NvdPage,NvdResponseParser,NvdRequestThrottle,NvdArticleCollector}.java` | NVD |
| `SB-Domain-Advisory/.../advisory/collector/{GhsaAdvisoryParser,GhsaArticleCollector}.java` | GitHub 보안 권고 |
| `SB-Domain-Advisory/.../advisory/ai/{ArticleTitleAnalyzer,ArticleTitleAnalysisRequest,ArticleTitleAnalysis,ArticleTitleAnalysisPolicy}.java` | AI 분석 계약·결과 검증 |
| `SB-Domain-Advisory/.../advisory/ai/{AnthropicArticleTitleAnalyzer,ArticleTitleAnalysisPrompt}.java` | Anthropic SDK 구현·프롬프트·JSON 스키마 |
| `SB-Domain-Advisory/.../advisory/service/{ArticleSaveService,SaveOutcome}.java` | 중복 제거·CVE 병합 저장 |
| `SB-Domain-Advisory/.../advisory/service/{TodayQueryService,ArticleQueryService,WeeklyTrendQueryService,SourceQueryService,ArticleCursor,ArticleSpecifications}.java` | 조회 |
| `SB-Domain-Advisory/.../advisory/dto/*.java` | 응답·검색 조건 형태 |
| `SB-Application/.../application/collect/{ArticleCollectService,CollectRunRecorder,CollectRunResult}.java` | 수집 유스케이스 + `collect_run` 기록 |
| `SB-Application/.../application/ai/{ArticleAiProcessService,ArticleAiResultRecorder}.java` | AI 묶음 처리 유스케이스 |
| `SB-Web/.../web/config/ClockConfig.java` | `Clock` 빈 (Asia/Seoul) |
| `SB-Web/.../web/scheduler/{ArticleCollectScheduler,ArticleAiScheduler}.java` | 1분 수집 틱, 5분 AI 틱 |
| `SB-Web/.../web/controller/{Today,Article,Trend,Source}Controller.java` | API |
| `.github/workflows/PROJECT-SPRING-SYNOLOGY-SECBRIEF-CICD.yaml` | 컨테이너에 `ANTHROPIC_API_KEY` 전달 |

모듈 배치 근거: 서버 CLAUDE.md 가 `SB-Domain-Advisory` 를 "수집한 보안 공지·뉴스", `SB-Application` 을 "수집 > 정리 > 알림 유스케이스"로 정함. 외부 호출 구현(수집기·AI 분석기)은 수집기와 같은 모듈에 두고 유스케이스는 인터페이스만 안다. DB 가 필요한 테스트는 Flyway 파일이 있는 `SB-Web` 에 둔다.

### 앱 (`mobile/`)

| 파일 | 책임 |
|---|---|
| `package.json` | jest `moduleNameMapper` (`@/`) |
| `src/core/config/{support-links,app-info}.ts` | 문의·약관 주소 단일 원본, 앱 버전 |
| `src/core/error/app-failure.ts` | 앱 자체 코드 상수 추가 |
| `src/core/format/briefing-date-formatter.ts` | `toArticleDateLabel` 추가 |
| `src/data/sources/secbrief-api-client.ts` | fetch + 시간 제한 + 에러 코드 변환 (앱 유일한 `fetch(`) |
| `src/data/sources/{today-briefing,article,weekly-trend,collect-source-status}-source.ts` | 엔드포인트별 조회 |
| `src/data/mappers/api-response-mapper.ts` | 응답 JSON > 모델 |
| `src/data/models/{briefing-item,today-briefing,trend-topic,article-page,collect-source-status}.ts` | 모델 |
| `src/data/sources/sample-today-briefing-source.ts` | **삭제** (스펙 §9·반목표: 운영 빌드에 샘플 금지) |
| `src/design/components/{sb-article-row,sb-list-row,sb-filter-chips}.tsx` | 목록 행·설정 행·필터 칩 |
| `src/features/articles/{article-row-view,open-original-article,use-pull-refresh}.ts` | 탭 공통 |
| `src/features/trend/trend-view-state.ts` | 동향 문구 단일 원본 (오늘 탭도 사용) |
| `src/features/{today,news,vulnerability,trend,settings}/...` | 탭별 화면·훅 |
| `src/app/(tabs)/{news,vulnerability,trend,settings}.tsx` | 라우트 (얇게) |
| `tests/{data,core,features,config}/*.test.ts`, `tests/architecture/architecture-rules.test.ts` | 단위·규칙 테스트 (jest `testMatch` 가 `tests/**/*.test.ts` 만 봄) |
| `.github/workflows/PROJECT-REACT-NATIVE-EXPO-{ANDROID-PLAYSTORE-CICD,ANDROID-TEST-APK,IOS-TESTFLIGHT}.yaml` | `EXPO_PUBLIC_API_BASE_URL` |

---

## 서버

서버 공통 준비: `cd server && export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. DB 테스트는 Docker 가 켜져 있어야 실행된다(꺼져 있으면 SKIPPED — PASS 로 치지 않는다).

### Task 1: 스키마·엔티티·저장소·출처 시드 + DB 테스트 기반

**Files:**
- Modify: `server/build.gradle` (subprojects 테스트 의존성)
- Create: `server/SB-Web/src/main/resources/db/migration/V0.3.0__create_source_article_collect_run.sql`
- Create: `server/SB-Domain-Advisory/src/main/java/kr/suhsaechan/secbrief/advisory/constant/{ArticleKind,ArticleRegion,ArticleTopic,AiStatus,FeedFormat,CollectStatus,SourceCode}.java`
- Create: `server/SB-Domain-Advisory/src/main/java/kr/suhsaechan/secbrief/advisory/entity/{Source,Article,CollectRun}.java`
- Create: `server/SB-Domain-Advisory/src/main/java/kr/suhsaechan/secbrief/advisory/repository/{SourceRepository,ArticleRepository,CollectRunRepository}.java`
- Test: `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/support/PostgresContainerTestSupport.java`, `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/schema/AdvisorySchemaTest.java`, `server/SB-Web/src/test/resources/application-test.yml`

**Interfaces:**
- Produces:
  - `enum ArticleKind { NEWS, ADVISORY, VULNERABILITY }`, `enum ArticleRegion { DOMESTIC, OVERSEAS }`
  - `enum ArticleTopic { VULNERABILITY, RANSOMWARE, PRIVACY, REMOTE_ACCESS, DATABASE, SUPPLY_CHAIN, POLICY, GENERAL }` + `String getApiValue()` (`remoteAccess`, `supplyChain` 등 lowerCamel — AI JSON 스키마 enum 값) + `static Optional<ArticleTopic> fromApiValue(String apiValue)`
  - `enum AiStatus { PENDING, DONE, FAILED }`
  - `enum FeedFormat { RSS, CISA_KEV_JSON, NVD_API, GHSA_API }`, `enum CollectStatus { SUCCESS, FAILED }`
  - `final class SourceCode` 상수: `KISA`, `CISA_KEV`, `NVD`, `DAILYSECU`, `THE_HACKER_NEWS`, `BLEEPING_COMPUTER`, `GHSA` (값 = 이름)
  - `Source extends BaseEntity` (getter: `id, code, displayName, kind, region, feedFormat, feedUrl, intervalMinutes, isEnabled, lastCollectedAt(Instant), lastStatus(CollectStatus)`; `void recordCollectResult(CollectStatus status, Instant finishedAt)` — SUCCESS 일 때만 `lastCollectedAt` 갱신)
  - `Article extends BaseEntity` (getter 전 컬럼, boolean getter 는 `isKev()`·`isUrgent()`·`isPublishedAtEstimated()`; 메서드 `create`·`markKev`·`replaceLinkWithNvd` 는 Task 8, `applyAiAnalysis`·`recordAiFailure` 는 Task 10)
  - `CollectRun` (`static CollectRun start(Source source, Instant startedAt)`; `void finishSuccess(Instant finishedAt, int newCount)`; `void finishFailed(Instant finishedAt, String errorCode, String sanitizedErrorMessage)`)
  - `SourceRepository extends JpaRepository<Source, Long>`: `List<Source> findAllByIsEnabledTrueOrderByIdAsc()`, `Optional<Source> findByCode(String code)`
  - `ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article>`: `Optional<Article> findByCveId(String cveId)`, `boolean existsByOriginalUrl(String originalUrl)`, `List<Article> findByAiStatusOrderByIdAsc(AiStatus aiStatus, Limit limit)`
  - `CollectRunRepository extends JpaRepository<CollectRun, Long>`: `Optional<CollectRun> findTopBySourceIdOrderByStartedAtDesc(Long sourceId)`
  - `abstract class PostgresContainerTestSupport` — `@Testcontainers(disabledWithoutDocker = true)`, `@Container @ServiceConnection static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")`

스키마 결정 (스펙 §7 + 추가분):
- `source`: 스펙 컬럼 + `feed_format varchar(32) not null`(수집기 선택용, 스펙 §12 "빌더에게 맡김") + `created_at`, `updated_at`(BaseEntity). `code` unique.
- `article`: `title varchar(500)`(원문 제목), `translated_title varchar(500) null`, `original_url varchar(1000) unique`, `publisher_name varchar(100)`, `topic varchar(32) not null default 'GENERAL'`, `ai_status varchar(16) not null default 'PENDING'`, `ai_attempt_count int not null default 0`, `cve_id varchar(32) unique null`, `cvss_score numeric(3,1)`, `cvss_version varchar(8)`, `published_at timestamptz not null`, `collected_at timestamptz not null`, enum 은 `varchar` + `@Enumerated(STRING)`, `created_at`, `updated_at`. 인덱스 `(published_at desc, id desc)`, `(kind, region, published_at desc)`, `(ai_status, id)`.
- `collect_run`: `status varchar(16)`, `new_count int`, `error_code varchar(64)`, `error_message varchar(500)`, 인덱스 `(source_id, started_at desc)`.
- 시드 7행 (스펙 §5 그대로, `is_enabled = true`):

| code | display_name | kind | region | feed_format | feed_url | interval_minutes |
|---|---|---|---|---|---|---|
| KISA | KISA 보호나라 | ADVISORY | DOMESTIC | RSS | `https://www.boho.or.kr/kr/rss.do?bbsId=B0000133` | 30 |
| CISA_KEV | CISA KEV | VULNERABILITY | OVERSEAS | CISA_KEV_JSON | `https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json` | 360 |
| NVD | NVD | VULNERABILITY | OVERSEAS | NVD_API | `https://services.nvd.nist.gov/rest/json/cves/2.0` | 120 |
| DAILYSECU | 데일리시큐 | NEWS | DOMESTIC | RSS | `https://www.dailysecu.com/rss/allArticle.xml` | 30 |
| THE_HACKER_NEWS | The Hacker News | NEWS | OVERSEAS | RSS | `https://feeds.feedburner.com/TheHackersNews` | 30 |
| BLEEPING_COMPUTER | BleepingComputer | NEWS | OVERSEAS | RSS | `https://www.bleepingcomputer.com/feed/` | 30 |
| GHSA | GitHub 보안 권고 | VULNERABILITY | OVERSEAS | GHSA_API | `https://api.github.com/advisories?type=reviewed&severity=critical` | 120 |

- [ ] **Step 1: 테스트 의존성 추가** — `server/build.gradle` subprojects `dependencies` 에 `testImplementation 'org.springframework.boot:spring-boot-testcontainers'`, `testImplementation 'org.testcontainers:junit-jupiter'`, `testImplementation 'org.testcontainers:postgresql'` (버전은 Boot BOM). `application-test.yml`: `secbrief.collect.enabled: false`, `secbrief.ai.enabled: false`, `secbrief.ai.anthropic-api-key: ""`.

- [ ] **Step 2: 실패 테스트 작성** — `AdvisorySchemaTest` (`@SpringBootTest @ActiveProfiles("test")`, `PostgresContainerTestSupport` 상속)

```java
@Test void flywayMigratesAndJpaValidatesSchema() {
    // 컨텍스트가 뜨면 ddl-auto=validate 통과
    assertThat(sourceRepository.count()).isEqualTo(7);
}
@Test void seedsSevenEnabledSourcesWithSpecIntervals() {
    Map<String, Integer> intervalByCode = sourceRepository.findAllByIsEnabledTrueOrderByIdAsc().stream()
        .collect(toMap(Source::getCode, Source::getIntervalMinutes));
    assertThat(intervalByCode).containsExactlyInAnyOrderEntriesOf(Map.of(
        "KISA", 30, "CISA_KEV", 360, "NVD", 120, "DAILYSECU", 30, "THE_HACKER_NEWS", 30,
        "BLEEPING_COMPUTER", 30, "GHSA", 120));
}
@Test void seedFeedFormatsMatchCollectorTypes() {
    assertThat(sourceRepository.findByCode("NVD").orElseThrow().getFeedFormat()).isEqualTo(FeedFormat.NVD_API);
    assertThat(sourceRepository.findByCode("CISA_KEV").orElseThrow().getFeedFormat()).isEqualTo(FeedFormat.CISA_KEV_JSON);
    assertThat(sourceRepository.findByCode("GHSA").orElseThrow().getFeedFormat()).isEqualTo(FeedFormat.GHSA_API);
}
@Test void newArticleDefaultsToPendingGeneral() { /* jdbcTemplate 로 topic·ai 컬럼 없이 insert > topic GENERAL, ai_status PENDING, ai_attempt_count 0 */ }
@Test void rejectsDuplicateOriginalUrl() { /* 같은 original_url 2행 insert > DataIntegrityViolationException */ }
@Test void allowsManyArticlesWithoutCveId() { /* cve_id null 2행 insert 성공 */ }
@Test void topicApiValuesRoundTrip() {
    assertThat(ArticleTopic.SUPPLY_CHAIN.getApiValue()).isEqualTo("supplyChain");
    assertThat(ArticleTopic.fromApiValue("remoteAccess")).contains(ArticleTopic.REMOTE_ACCESS);
    assertThat(ArticleTopic.fromApiValue("malware")).isEmpty();
}
```

- [ ] **Step 3: 실패 확인** — `./gradlew :SB-Web:test --tests '*AdvisorySchemaTest*'` → FAIL (테이블 없음)
- [ ] **Step 4: 마이그레이션·enum·엔티티·저장소 구현** (위 Interfaces 그대로)
- [ ] **Step 5: 통과 확인** — 같은 명령 → PASS 7건
- [ ] **Step 6: 커밋** — `/pro-commit` (`server/build.gradle`, 마이그레이션, advisory 패키지, 테스트 경로만)

### Task 2: 긴급 판정 규칙

**Files:**
- Create: `server/SB-Domain-Advisory/src/main/java/kr/suhsaechan/secbrief/advisory/policy/ArticleUrgencyPolicy.java`
- Test: `server/SB-Domain-Advisory/src/test/java/kr/suhsaechan/secbrief/advisory/policy/ArticleUrgencyPolicyTest.java`

**Interfaces:**
- Consumes: Task 1 `SourceCode`
- Produces:
  - `static boolean ArticleUrgencyPolicy.isUrgent(String sourceCode, String title, BigDecimal cvssScore, boolean isKev)`
  - 상수 `URGENT_CVSS_THRESHOLD = new BigDecimal("9.0")`, `KISA_URGENT_TITLE_KEYWORD = "긴급"`

(주제 분류는 규칙이 아니라 AI 가 맡는다 — Task 10·11.)

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void cvssEightNineIsNotUrgent() { assertThat(isUrgent("NVD", "x", new BigDecimal("8.9"), false)).isFalse(); }
@Test void cvssNineZeroIsUrgent() { assertThat(isUrgent("NVD", "x", new BigDecimal("9.0"), false)).isTrue(); }
@Test void ghsaCriticalScoreIsUrgent() { assertThat(isUrgent("GHSA", "x", new BigDecimal("9.3"), false)).isTrue(); }
@Test void kevIsUrgentWithoutScore() { assertThat(isUrgent("CISA_KEV", "x", null, true)).isTrue(); }
@Test void kisaTitleWithUrgentKeywordIsUrgent() { assertThat(isUrgent("KISA", "[긴급] Ivanti 보안 업데이트 권고", null, false)).isTrue(); }
@Test void urgentKeywordFromNonKisaSourceIsNotUrgent() { assertThat(isUrgent("DAILYSECU", "긴급 점검", null, false)).isFalse(); }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*ArticleUrgencyPolicyTest*'` → FAIL (클래스 없음)
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 3: 정규화·수집 주기 판정·서울 달력

**Files:**
- Modify: `server/SB-Domain-Advisory/build.gradle` (`id 'java-test-fixtures'`)
- Create: `.../advisory/collector/CollectedArticle.java`, `.../advisory/policy/{ArticleDraft,ArticleNormalizer,CollectSchedulePolicy,SeoulCalendar}.java`
- Create: `server/SB-Domain-Advisory/src/testFixtures/java/kr/suhsaechan/secbrief/advisory/support/SourceFixtures.java`
- Test: `server/SB-Domain-Advisory/src/test/java/kr/suhsaechan/secbrief/advisory/policy/{ArticleNormalizerTest,CollectSchedulePolicyTest,SeoulCalendarTest}.java`

**Interfaces:**
- Consumes: Task 2 urgency, Task 1 `Source`
- Produces:
  - `record CollectedArticle(String title, String originalUrl, String publisherName, Instant publishedAt /* null 허용 */, String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev)`
  - `record ArticleDraft(ArticleKind kind, ArticleRegion region, String title, String originalUrl, String publisherName, Instant publishedAt, boolean isPublishedAtEstimated, String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev, boolean isUrgent)` (주제 없음 — 저장 시 `GENERAL`·`PENDING`)
  - `static Optional<ArticleDraft> ArticleNormalizer.normalize(CollectedArticle collected, Source source, Instant collectedAt)` — 정리 후 제목이 비거나 URL 이 http(s) 가 아니면 `Optional.empty()`
  - `static String ArticleNormalizer.toSanitizedErrorMessage(String rawMessage)` — 제어문자 > `?`, 500자 제한, null > `"unknown"`
  - `static boolean CollectSchedulePolicy.isCollectDue(Instant lastAttemptStartedAt /* null 허용 */, int intervalMinutes, Instant now)`
  - `static Instant CollectSchedulePolicy.toCollectSince(Source source, Instant now)` — RSS 는 null, KEV·NVD·GHSA 는 `max(lastCollectedAt - 1일, now - 첫수집일수)` (`lastCollectedAt` null 이면 뒤쪽 값)
  - `SeoulCalendar.SEOUL = ZoneId.of("Asia/Seoul")`, `static Instant startOfToday(Instant now)`, `static Instant startOfWeek(Instant now)` (월요일 00:00), `static LocalDate toSeoulDate(Instant now)`
  - testFixtures: `SourceFixtures.source(String code, ArticleKind kind, ArticleRegion region, FeedFormat feedFormat, Instant lastCollectedAt): Source` (`ReflectionTestUtils` 로 필드 채움 — 엔티티에 테스트용 공개 생성자를 만들지 않음). `SB-Application`·`SB-Web` 은 `testImplementation testFixtures(project(':SB-Domain-Advisory'))`

정규화 규칙: 제목은 HTML 태그 제거 > 연속 공백 하나로 > trim > 500자 자르기. URL 은 trim, `#fragment` 제거. `publishedAt` 이 null 이면 `collectedAt` + 추정 표시, `collectedAt + 1시간` 보다 미래면 `collectedAt` + 추정 표시. `kind`·`region` 은 `Source` 값. 게시자는 수집값이 비면 `source.displayName`. 긴급은 Task 2 규칙.

상수: `FUTURE_PUBLISHED_AT_TOLERANCE = Duration.ofHours(1)`, `COLLECT_OVERLAP = Duration.ofDays(1)`, `KEV_FIRST_COLLECT_DAYS = 30`, `NVD_FIRST_COLLECT_DAYS = 7`, `GHSA_FIRST_COLLECT_DAYS = 7`, `MAXIMUM_TITLE_LENGTH = 500`, `MAXIMUM_ERROR_MESSAGE_LENGTH = 500`.

- [ ] **Step 1: 실패 테스트 작성**

```java
static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

@Test void usesCollectedAtWhenPublishedAtMissing() {
    ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", null), kisaSource, NOW).orElseThrow();
    assertThat(draft.publishedAt()).isEqualTo(NOW);
    assertThat(draft.isPublishedAtEstimated()).isTrue();
}
@Test void clampsFuturePublishedAtToNow() {
    ArticleDraft draft = normalize(collected("제목", "https://a.kr/1", NOW.plus(Duration.ofHours(9))), kisaSource, NOW).orElseThrow();
    assertThat(draft.publishedAt()).isEqualTo(NOW);
    assertThat(draft.isPublishedAtEstimated()).isTrue();
}
@Test void keepsPublishedAtWithinTolerance() { /* NOW+30분 > 그대로, 추정 아님 */ }
@Test void stripsHtmlAndCollapsesWhitespaceInTitle() {
    assertThat(normalize(collected("  <b>Ivanti</b>\n  긴급 패치 ", "https://a.kr/1", NOW), kisaSource, NOW).orElseThrow().title())
        .isEqualTo("Ivanti 긴급 패치");
}
@Test void dropsFragmentFromOriginalUrl() { /* "https://a.kr/1#top" > "https://a.kr/1" */ }
@Test void rejectsBlankTitleOrNonHttpUrl() { /* "<br>" 제목, "javascript:alert(1)" URL > Optional.empty() */ }
@Test void appliesSourceKindRegionAndUrgency() {
    ArticleDraft draft = normalize(collected("[긴급] Ivanti 보안 업데이트 권고", "https://a.kr/2", NOW), kisaSource, NOW).orElseThrow();
    assertThat(draft.kind()).isEqualTo(ADVISORY);
    assertThat(draft.region()).isEqualTo(DOMESTIC);
    assertThat(draft.isUrgent()).isTrue();
}
@Test void sanitizesControlCharactersAndLimitsLength() {
    assertThat(toSanitizedErrorMessage("a\nb\u0000c")).isEqualTo("a?b?c");
    assertThat(toSanitizedErrorMessage("x".repeat(600))).hasSize(500);
    assertThat(toSanitizedErrorMessage(null)).isEqualTo("unknown");
}

@Test void isCollectDueWhenNeverAttempted() { assertThat(isCollectDue(null, 30, NOW)).isTrue(); }
@Test void isCollectDueUsesLastAttemptNotLastSuccess() {
    assertThat(isCollectDue(NOW.minus(Duration.ofMinutes(29)), 30, NOW)).isFalse();
    assertThat(isCollectDue(NOW.minus(Duration.ofMinutes(30)), 30, NOW)).isTrue();
}
@Test void firstKevCollectStartsThirtyDaysAgo() { assertThat(toCollectSince(kevSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(30))); }
@Test void firstNvdCollectStartsSevenDaysAgo() { assertThat(toCollectSince(nvdSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7))); }
@Test void firstGhsaCollectStartsSevenDaysAgo() { assertThat(toCollectSince(ghsaSourceNeverCollected, NOW)).isEqualTo(NOW.minus(Duration.ofDays(7))); }
@Test void laterNvdCollectOverlapsOneDay() { /* lastCollectedAt = NOW-2h > since = NOW-2h-1일 */ }
@Test void longOutageNvdCollectIsCappedAtSevenDays() { /* lastCollectedAt = NOW-30일 > since = NOW-7일 */ }
@Test void rssHasNoSince() { assertThat(toCollectSince(kisaSource, NOW)).isNull(); }

@Test void startOfWeekIsMondayMidnightSeoul() {
    // 2026-11-25(수) 12:00 KST > 2026-11-23(월) 00:00 KST = 2026-11-22T15:00Z
    assertThat(startOfWeek(Instant.parse("2026-11-25T03:00:00Z"))).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
}
@Test void startOfTodayUsesSeoulDate() {
    // 2026-11-23T16:30Z = 11-24 01:30 KST > 11-24 00:00 KST
    assertThat(startOfToday(Instant.parse("2026-11-23T16:30:00Z"))).isEqualTo(Instant.parse("2026-11-23T15:00:00Z"));
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*ArticleNormalizerTest*' --tests '*CollectSchedulePolicyTest*' --tests '*SeoulCalendarTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 4: RSS 수집기 (KISA, 데일리시큐, The Hacker News, BleepingComputer)

**Files:**
- Modify: `server/SB-Domain-Advisory/build.gradle` (`implementation 'com.rometools:rome:2.1.0'`)
- Modify: `server/SB-Common/src/main/java/kr/suhsaechan/secbrief/common/exception/constant/ErrorCode.java` (외부 연동 구역에 `FEED_PARSE_ERROR(HttpStatus.BAD_GATEWAY, "외부 소식 형식을 읽지 못했어요.")`)
- Create: `.../advisory/collector/{ArticleCollector,FeedHttpClient,RssFeedParser,RssArticleCollector}.java`
- Test: `.../advisory/collector/RssFeedParserTest.java`, fixtures `server/SB-Domain-Advisory/src/test/resources/fixtures/rss/{kisa-notice,dailysecu,the-hacker-news,bleeping-computer}.xml`

**Interfaces:**
- Consumes: Task 3 `CollectedArticle`, Task 1 `Source`, `FeedFormat`
- Produces:
  - `interface ArticleCollector { FeedFormat supportedFeedFormat(); List<CollectedArticle> collect(Source source, Instant since /* null 허용 */); }`
  - `@Component FeedHttpClient`: `byte[] fetch(String url)`, `byte[] fetch(String url, Map<String, String> extraHeaders)` — `RestClient`, 연결 5초·읽기 20초, 헤더 `User-Agent: secbrief-collector/1.0 (+https://cassiiopeia.github.io/secbrief/support.html)`, 2xx 아니면 `CustomException(EXTERNAL_API_ERROR, "status=" + code)`, 연결 실패도 같은 코드
  - `static List<CollectedArticle> RssFeedParser.parse(InputStream feedStream, String publisherName)` — Rome `SyndFeedInput` + `XmlReader`(인코딩 자동), 날짜는 `publishedDate` 없으면 `updatedDate`, 둘 다 없으면 null; 파싱 실패 시 `CustomException(FEED_PARSE_ERROR, ...)`
  - `@Component RssArticleCollector implements ArticleCollector` (`FeedFormat.RSS`, 게시자 = `source.displayName` — 피드 `author` 는 기자 이름이라 게시자로 쓰지 않음)

Fixture 만드는 법: 각 피드를 `curl -sL -A 'secbrief-collector/1.0' '<feed_url>' -o /tmp/<name>.xml` 로 한 번 받아 `<item>` 2개만 남겨 저장 (공개 출처의 공개 제목만). `kisa-notice.xml` 두 번째 항목은 `pubDate` 를 지워 null 경로 확인용으로 둔다. 받는 도중 403 이면 User-Agent 차단이니 기록한다.

- [ ] **Step 1: fixture 저장 + 실패 테스트 작성**

```java
@ParameterizedTest @ValueSource(strings = {"kisa-notice", "dailysecu", "the-hacker-news", "bleeping-computer"})
void parsesTwoItemsWithTitleAndHttpLink(String fixtureName) {
    List<CollectedArticle> articles = parse(fixture(fixtureName), "게시자");
    assertThat(articles).hasSize(2).allSatisfy(article -> {
        assertThat(article.title()).isNotBlank();
        assertThat(article.originalUrl()).startsWith("http");
        assertThat(article.publisherName()).isEqualTo("게시자");
        assertThat(article.cveId()).isNull();
    });
}
@Test void returnsNullPublishedAtWhenItemHasNoDate() { assertThat(parse(fixture("kisa-notice"), "KISA").get(1).publishedAt()).isNull(); }
@Test void decodesKoreanTitleFromKisaFeed() { assertThat(parse(fixture("kisa-notice"), "KISA").get(0).title()).containsPattern("[가-힣]"); }
@Test void throwsFeedParseErrorOnBrokenXml() {
    assertThatThrownBy(() -> parse(new ByteArrayInputStream("<rss><".getBytes(UTF_8)), "x"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.FEED_PARSE_ERROR);
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*RssFeedParserTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS, `./gradlew compileJava` → BUILD SUCCESSFUL
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 5: CISA KEV 수집기

**Files:**
- Create: `.../advisory/collector/{KevCatalogParser,KevArticleCollector}.java`
- Test: `.../advisory/collector/KevCatalogParserTest.java`, fixture `server/SB-Domain-Advisory/src/test/resources/fixtures/kev/kev-catalog.json`

**Interfaces:**
- Consumes: Task 4 `ArticleCollector`, `FeedHttpClient`
- Produces:
  - `static List<CollectedArticle> KevCatalogParser.parse(InputStream catalogStream, Instant since)` (Jackson)
  - `@Component KevArticleCollector implements ArticleCollector` (`FeedFormat.CISA_KEV_JSON`)

매핑: `title = cveID + ": " + vulnerabilityName`, `originalUrl = "https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=" + cveID`, `publisherName = "CISA KEV"`, `publishedAt = dateAdded 00:00 UTC`, `cveId = cveID`, `cvssScore = null`, `isKev = true`. `dateAdded < since` 는 버림.

Fixture: 실제 카탈로그 형식(`title`, `catalogVersion`, `vulnerabilities[]`)으로 3건 — `dateAdded` 2026-11-20(`CVE-2026-1111`), 2026-11-10(`CVE-2026-2222`), 2026-09-01(`CVE-2026-3333`).

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void keepsOnlyEntriesAddedSinceWindow() {
    List<CollectedArticle> articles = parse(fixture(), Instant.parse("2026-10-24T00:00:00Z"));
    assertThat(articles).extracting(CollectedArticle::cveId).containsExactly("CVE-2026-1111", "CVE-2026-2222");
}
@Test void mapsKevEntryToArticle() {
    CollectedArticle article = parse(fixture(), Instant.EPOCH).get(0);
    assertThat(article.title()).startsWith("CVE-2026-1111: ");
    assertThat(article.isKev()).isTrue();
    assertThat(article.cvssScore()).isNull();
    assertThat(article.publishedAt()).isEqualTo(Instant.parse("2026-11-20T00:00:00Z"));
    assertThat(article.publisherName()).isEqualTo("CISA KEV");
}
@Test void throwsFeedParseErrorWhenVulnerabilitiesMissing() { /* {"title":"x"} > FEED_PARSE_ERROR */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*KevCatalogParserTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 6: NVD 수집기 (CVSS 9.0 이상, v3.1 > v4.0, 6초 간격)

**Files:**
- Create: `.../advisory/collector/{NvdPage,NvdResponseParser,NvdRequestThrottle,NvdArticleCollector}.java`
- Test: `.../advisory/collector/{NvdResponseParserTest,NvdRequestThrottleTest}.java`, fixture `server/SB-Domain-Advisory/src/test/resources/fixtures/nvd/nvd-cves-page.json`

**Interfaces:**
- Consumes: Task 4 `ArticleCollector`, `FeedHttpClient`, `Clock` 빈(Task 9 에서 등록 — 이 Task 테스트는 직접 생성)
- Produces:
  - `record NvdPage(List<CollectedArticle> articles, int startIndex, int resultsPerPage, int totalResults)`
  - `static NvdPage NvdResponseParser.parse(InputStream responseStream)` (9.0 미만은 걸러서 반환)
  - `NvdRequestThrottle(Clock clock, Sleeper sleeper)` + `synchronized void awaitTurn()` — 직전 요청 후 `MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(6)` 이 안 지났으면 남은 시간만큼 대기. 중첩 `interface Sleeper { void sleep(Duration duration) throws InterruptedException; }` (운영 구현은 `Thread.sleep`)
  - `@Component NvdArticleCollector implements ArticleCollector` (`FeedFormat.NVD_API`) — 요청 `{feedUrl}?pubStartDate={since}&pubEndDate={now}&resultsPerPage=2000&startIndex={n}`, 날짜 형식 `yyyy-MM-dd'T'HH:mm:ss.SSS` UTC (오프셋 없으면 NVD 가 UTC 로 해석 — Task 9 Step 5 실수집으로 확인), `startIndex + resultsPerPage < totalResults` 인 동안 다음 페이지, 매 요청 전 `awaitTurn()`

점수 규칙: `metrics.cvssMetricV31[]` 중 `type == "Primary"` 우선, 없으면 첫 항목의 `cvssData.baseScore`, version `"3.1"`. v3.1 이 아예 없을 때만 `cvssMetricV40[]` 같은 방식, version `"4.0"`. 둘 다 없으면 버림. 점수 < 9.0 버림.
매핑: `title = id + ": " + descriptions[lang=en] 앞 120자(넘으면 끝에 "…")`, `originalUrl = "https://nvd.nist.gov/vuln/detail/" + id`, `publisherName = "NVD"`, `publishedAt = published` (UTC 로 해석), `isKev = false`.

Fixture: 실제 API 응답 형식으로 4건 — A `CVE-2026-0001` v3.1 Primary 9.8(설명 200자) / B `CVE-2026-0002` v4.0 만 9.3 / C `CVE-2026-0003` v3.1 7.5 / D `CVE-2026-0004` v3.1 8.8 + v4.0 9.5. `totalResults: 4`.

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void keepsOnlyCriticalScoresPreferringV31() {
    NvdPage page = parse(fixture());
    assertThat(page.articles()).extracting(CollectedArticle::cveId, CollectedArticle::cvssScore, CollectedArticle::cvssVersion)
        .containsExactly(tuple("CVE-2026-0001", new BigDecimal("9.8"), "3.1"),
                         tuple("CVE-2026-0002", new BigDecimal("9.3"), "4.0"));
    // D(v3.1 8.8 + v4.0 9.5)는 v3.1 우선이라 제외
}
@Test void truncatesDescriptionTitleTo120Characters() {
    String title = parse(fixture()).articles().get(0).title();
    assertThat(title).startsWith("CVE-2026-0001: ").endsWith("…");
    assertThat(title).hasSize("CVE-2026-0001: ".length() + 120 + 1);
}
@Test void readsPagingNumbers() { assertThat(parse(fixture()).totalResults()).isEqualTo(4); }
@Test void parsesPublishedWithoutOffsetAsUtc() { /* "2026-11-20T15:15:10.123" > 2026-11-20T15:15:10.123Z */ }

@Test void firstRequestDoesNotWait() { /* 고정 시계 > sleeper 호출 없음 */ }
@Test void secondRequestWaitsRemainingInterval() {
    // 첫 호출 t0, 시계를 t0+2s 로 옮긴 뒤 두 번째 호출 > sleeper 가 Duration.ofSeconds(4) 로 1회 호출
}
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*NvdResponseParserTest*' --tests '*NvdRequestThrottleTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 7: GitHub 보안 권고(GHSA) 수집기

**Files:**
- Create: `.../advisory/collector/{GhsaAdvisoryParser,GhsaArticleCollector}.java`
- Test: `.../advisory/collector/GhsaAdvisoryParserTest.java`, fixture `server/SB-Domain-Advisory/src/test/resources/fixtures/ghsa/ghsa-advisories.json`

**Interfaces:**
- Consumes: Task 4 `ArticleCollector`, `FeedHttpClient.fetch(url, extraHeaders)`
- Produces:
  - `static List<CollectedArticle> GhsaAdvisoryParser.parse(InputStream responseStream, Instant since)` (Jackson, 응답은 권고 배열)
  - `@Component GhsaArticleCollector implements ArticleCollector` (`FeedFormat.GHSA_API`) — 요청 `{feedUrl}&per_page=100&sort=published&direction=desc`, 헤더 `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`, 인증 없음(시간당 60회, 2시간 주기라 1회/2시간). 한 페이지만 읽는다(주간 critical 권고가 100건을 넘지 않는다는 가정 — 넘으면 로그 `[article collect] ghsa page full` 남김)

매핑 (필드 이름은 구현 전에 GitHub REST 문서 "List global security advisories" 로 확인): `title = summary`, `originalUrl = html_url`, `publisherName = "GitHub Advisory"`, `publishedAt = published_at`, `cveId = cve_id`(null 이면 null — 이때 중복 제거는 `original_url` 로), 점수는 `cvss_severities.cvss_v3.score` 우선, 없거나 0 이면 `cvss_severities.cvss_v4.score`, 둘 다 없으면 null; `cvssVersion` 은 `vector_string` 접두사(`CVSS:3.1/` > `"3.1"`, `CVSS:4.0/` > `"4.0"`). `isKev = false`. `published_at < since` 는 버림.

Fixture: 3건 — E `GHSA-aaaa-bbbb-cccc` CVE `CVE-2026-5555` v3.1 9.8, published 2026-11-21 / F `GHSA-dddd-eeee-ffff` CVE 없음 v4.0 9.3, published 2026-11-20 / G 2026-10-01 published (기간 밖).

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void mapsAdvisoryWithCveAndV3Score() {
    CollectedArticle article = parse(fixture(), Instant.parse("2026-11-16T00:00:00Z")).get(0);
    assertThat(article.cveId()).isEqualTo("CVE-2026-5555");
    assertThat(article.originalUrl()).isEqualTo("https://github.com/advisories/GHSA-aaaa-bbbb-cccc");
    assertThat(article.publisherName()).isEqualTo("GitHub Advisory");
    assertThat(article.cvssScore()).isEqualByComparingTo("9.8");
    assertThat(article.cvssVersion()).isEqualTo("3.1");
}
@Test void keepsAdvisoryWithoutCveUsingHtmlUrl() {
    CollectedArticle article = parse(fixture(), Instant.parse("2026-11-16T00:00:00Z")).get(1);
    assertThat(article.cveId()).isNull();
    assertThat(article.cvssVersion()).isEqualTo("4.0");
}
@Test void dropsAdvisoriesPublishedBeforeSince() {
    assertThat(parse(fixture(), Instant.parse("2026-11-16T00:00:00Z"))).hasSize(2);
}
@Test void throwsFeedParseErrorWhenNotArray() { /* {"message":"API rate limit exceeded"} > FEED_PARSE_ERROR */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*GhsaAdvisoryParserTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 8: 중복 제거·CVE 병합 저장

**Files:**
- Create: `.../advisory/service/{ArticleSaveService,SaveOutcome}.java`
- Modify: `.../advisory/entity/Article.java` (`create`, `markKev`, `replaceLinkWithNvd`)
- Test: `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/advisory/ArticleSaveServiceTest.java` (Testcontainers)

**Interfaces:**
- Consumes: Task 1 저장소·엔티티, Task 3 `ArticleDraft`, `SourceCode`
- Produces:
  - `static Article Article.create(Source source, ArticleDraft draft, Instant collectedAt)` — `topic = GENERAL`, `aiStatus = PENDING`, `aiAttemptCount = 0`, `translatedTitle = null`
  - `void markKev()`, `void replaceLinkWithNvd(Source nvdSource, ArticleDraft nvdDraft)` — 제목이 바뀌므로 `translatedTitle = null`, `aiStatus = PENDING`, `aiAttemptCount = 0` 으로 되돌림 (새 제목으로 다시 번역)
  - `enum SaveOutcome { CREATED, MERGED, DUPLICATE }`
  - `@Service ArticleSaveService`: `@Transactional SaveOutcome saveOrMerge(Source source, ArticleDraft draft, Instant collectedAt)`

규칙 (NVD 가 링크 원본):
1. `cveId` 있음 + 같은 CVE 행 있음 →
   - draft 출처가 `SourceCode.NVD` 이고 기존 행 출처가 NVD 가 아니면(KEV·GHSA) `replaceLinkWithNvd`
   - draft `isKev` 이고 기존이 KEV 아니면 `markKev()`
   - GHSA draft 가 기존 NVD·KEV 행을 만나면 링크·게시자 유지 (바꿀 것 없으면 DUPLICATE)
   - `isUrgent = 기존 || draft`
   - 바뀐 게 있으면 MERGED, 없으면 DUPLICATE
2. 그 밖(CVE 없는 GHSA·RSS 포함) → `existsByOriginalUrl` 이면 DUPLICATE, 아니면 `Article.create` 저장 후 CREATED

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void mergesKevFirstThenNvdIntoSingleNvdRow() {
    saveOrMerge(kevSource, kevDraft("CVE-2026-1111"), NOW);
    SaveOutcome outcome = saveOrMerge(nvdSource, nvdDraft("CVE-2026-1111", "9.8"), NOW);
    Article merged = articleRepository.findByCveId("CVE-2026-1111").orElseThrow();
    assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
    assertThat(articleRepository.count()).isEqualTo(1);
    assertThat(merged.getOriginalUrl()).isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-1111");
    assertThat(merged.getPublisherName()).isEqualTo("NVD");
    assertThat(merged.isKev()).isTrue();
    assertThat(merged.getCvssScore()).isEqualByComparingTo("9.8");
    assertThat(merged.getAiStatus()).isEqualTo(AiStatus.PENDING);
}
@Test void marksExistingNvdRowAsKevAndKeepsNvdLink() {
    saveOrMerge(nvdSource, nvdDraft("CVE-2026-2222", "9.1"), NOW);
    SaveOutcome outcome = saveOrMerge(kevSource, kevDraft("CVE-2026-2222"), NOW);
    Article merged = articleRepository.findByCveId("CVE-2026-2222").orElseThrow();
    assertThat(outcome).isEqualTo(SaveOutcome.MERGED);
    assertThat(merged.isKev()).isTrue();
    assertThat(merged.getPublisherName()).isEqualTo("NVD");
}
@Test void ghsaWithCveAlreadyFromNvdKeepsNvdLink() {
    saveOrMerge(nvdSource, nvdDraft("CVE-2026-5555", "9.8"), NOW);
    assertThat(saveOrMerge(ghsaSource, ghsaDraft("CVE-2026-5555"), NOW)).isEqualTo(SaveOutcome.DUPLICATE);
    assertThat(articleRepository.findByCveId("CVE-2026-5555").orElseThrow().getPublisherName()).isEqualTo("NVD");
}
@Test void replacesGhsaLinkWhenNvdArrivesLater() {
    saveOrMerge(ghsaSource, ghsaDraft("CVE-2026-6666"), NOW);
    assertThat(saveOrMerge(nvdSource, nvdDraft("CVE-2026-6666", "9.4"), NOW)).isEqualTo(SaveOutcome.MERGED);
    assertThat(articleRepository.findByCveId("CVE-2026-6666").orElseThrow().getOriginalUrl())
        .isEqualTo("https://nvd.nist.gov/vuln/detail/CVE-2026-6666");
}
@Test void dedupsGhsaWithoutCveByHtmlUrl() { /* cveId null GHSA draft 두 번 > CREATED, DUPLICATE */ }
@Test void returnsDuplicateForSameRssUrl() { /* 같은 RSS draft 두 번 > CREATED, DUPLICATE, count 1 */ }
@Test void newArticleStartsPendingGeneral() { /* CREATED 행 topic GENERAL, aiStatus PENDING */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Web:test --tests '*ArticleSaveServiceTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 9: 수집 유스케이스 + 수집 기록 + 수집 스케줄러

**Files:**
- Create: `server/SB-Application/src/main/java/kr/suhsaechan/secbrief/application/collect/{ArticleCollectService,CollectRunRecorder,CollectRunResult}.java`
- Create: `server/SB-Web/src/main/java/kr/suhsaechan/secbrief/web/config/ClockConfig.java`, `server/SB-Web/src/main/java/kr/suhsaechan/secbrief/web/scheduler/ArticleCollectScheduler.java`
- Modify: `server/SB-Web/src/main/resources/application.yml` (`secbrief.collect.enabled: ${COLLECT_ENABLED:true}`, `secbrief.collect.tick-milliseconds: 60000`, `secbrief.collect.initial-delay-milliseconds: 30000` + 값 뜻 주석)
- Modify: `server/SB-Web/src/main/java/kr/suhsaechan/secbrief/web/scheduler/package-info.java` (설명 갱신)
- Test: `server/SB-Application/src/test/java/kr/suhsaechan/secbrief/application/collect/ArticleCollectServiceTest.java` (Mockito, DB 없음)

**Interfaces:**
- Consumes: Task 3~8, Task 1 저장소
- Produces:
  - `@Bean Clock clock()` = `Clock.system(ZoneId.of("Asia/Seoul"))`
  - `record CollectRunResult(CollectStatus status, int newCount, String errorCode /* 성공이면 null */)`
  - `@Service ArticleCollectService(List<ArticleCollector> collectors, SourceRepository sourceRepository, CollectRunRepository collectRunRepository, ArticleSaveService articleSaveService, CollectRunRecorder collectRunRecorder, Clock clock)`:
    - `void collectDueSources()` — 켜진 출처마다 최근 `collect_run.startedAt` 으로 `isCollectDue` 판정, 출처별 예외는 잡고 다음 출처로
    - `CollectRunResult collectSource(Source source)`
  - `@Component CollectRunRecorder`: `@Transactional Long start(Long sourceId, Instant startedAt)`, `@Transactional void finishSuccess(Long runId, Long sourceId, Instant finishedAt, int newCount)`, `@Transactional void finishFailed(Long runId, Long sourceId, Instant finishedAt, String errorCode, String sanitizedErrorMessage)`
  - `@Component @ConditionalOnProperty(name = "secbrief.collect.enabled", havingValue = "true", matchIfMissing = true) ArticleCollectScheduler` — `@Scheduled(fixedDelayString = "${secbrief.collect.tick-milliseconds}", initialDelayString = "${secbrief.collect.initial-delay-milliseconds}") void collectDueSources()`

`collectSource` 흐름: 시작 기록 > 수집기 선택(`feedFormat`) > `toCollectSince` > 수집 > 건별 `normalize` + `saveOrMerge` (건별 `DataIntegrityViolationException` 은 DUPLICATE 로 세고 계속) > 성공 기록(`newCount` = CREATED 수) / 예외 시 실패 기록(`CustomException` 이면 `getErrorCode().name()`, 아니면 `EXTERNAL_API_ERROR`; 메시지는 `toSanitizedErrorMessage`; `lastCollectedAt` 유지). 로그: `[article collect] collected {code} : new={n}, merged={m}`, `[article collect] failed {httpStatus} : source={code}, code={errorCode}`.

- [ ] **Step 1: 실패 테스트 작성** (`Clock.fixed(NOW, SEOUL)`, `SourceFixtures` 사용)

```java
@Test void savesNormalizedArticlesAndRecordsSuccessWithNewCount() {
    // RSS 수집기 mock 2건, saveOrMerge 가 CREATED, DUPLICATE
    assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 1, null));
    verify(collectRunRecorder).finishSuccess(eq(RUN_ID), eq(kisaSource.getId()), eq(NOW), eq(1));
}
@Test void recordsFailedRunAndKeepsLastCollectedAt() {
    when(rssCollector.collect(any(), any())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
    CollectRunResult result = service.collectSource(kisaSource);
    assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
    assertThat(result.errorCode()).isEqualTo("EXTERNAL_API_ERROR");
    verify(collectRunRecorder, never()).finishSuccess(anyLong(), anyLong(), any(), anyInt());
}
@Test void unexpectedExceptionIsRecordedAsExternalApiError() { /* RuntimeException > errorCode "EXTERNAL_API_ERROR" */ }
@Test void countsUniqueViolationAsDuplicateAndContinues() {
    // 첫 건 saveOrMerge 가 DataIntegrityViolationException, 둘째 CREATED > SUCCESS, newCount 1
}
@Test void skipsDraftsThatFailNormalization() { /* 빈 제목 1건 + 정상 1건 > saveOrMerge 1회 */ }
@Test void picksCollectorByFeedFormat() { /* GHSA 출처 > GHSA 수집기만 호출 */ }
@Test void collectsOnlyDueSources() {
    // KISA 마지막 시도 10분 전(30분 주기) > 건너뜀, DAILYSECU 마지막 시도 31분 전 > 수집
}
@Test void oneSourceFailureDoesNotStopOthers() { /* KISA 예외, DAILYSECU 정상 > DAILYSECU finishSuccess 호출 */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Application:test --tests '*ArticleCollectServiceTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS, `./gradlew compileJava` → BUILD SUCCESSFUL
- [ ] **Step 5: 로컬 실수집 1회 확인** — `docker run -d --name secbrief-pg -e POSTGRES_DB=secbrief -e POSTGRES_USER=secbrief -e POSTGRES_PASSWORD=secbrief -p 5432:5432 postgres:16-alpine` > `DATABASE_PASSWORD=secbrief ./gradlew :SB-Web:bootRun` (AI 는 키가 없어 건너뜀) > 2분 뒤 `docker exec secbrief-pg psql -U secbrief -c "select s.code, r.status, r.new_count, r.error_code from collect_run r join source s on s.id = r.source_id order by r.id"` → 7개 출처 각 1행, 모두 `SUCCESS` 이고 `new_count > 0`(GHSA 는 최근 7일 critical 이 없으면 0 가능). 실패 출처가 있으면 원인(User-Agent 차단·인코딩·NVD 날짜 형식·GHSA 필드 이름)을 고친 뒤 다시. 컨테이너는 Task 22 실화면 확인까지 둔다
- [ ] **Step 6: 커밋** — `/pro-commit`

### Task 10: AI 결과 검증 규칙 + 기사 AI 상태 전이

**Files:**
- Create: `.../advisory/ai/{ArticleTitleAnalysisRequest,ArticleTitleAnalysis,ArticleTitleAnalysisPolicy}.java`
- Modify: `.../advisory/entity/Article.java` (`applyAiAnalysis`, `recordAiFailure`)
- Test: `server/SB-Domain-Advisory/src/test/java/kr/suhsaechan/secbrief/advisory/ai/ArticleTitleAnalysisPolicyTest.java`, `server/SB-Domain-Advisory/src/test/java/kr/suhsaechan/secbrief/advisory/entity/ArticleAiStateTest.java`

**Interfaces:**
- Consumes: Task 1 `ArticleTopic`·`AiStatus`·`ArticleRegion`, Task 8 `Article.create`
- Produces:
  - `record ArticleTitleAnalysisRequest(String title, ArticleRegion region)` — 제목만 (본문 없음)
  - `record ArticleTitleAnalysis(ArticleTopic topic, String translatedTitle /* null 허용 */)`
  - `static String ArticleTitleAnalysisPolicy.toAcceptedTranslation(String originalTitle, ArticleRegion region, String translatedTitle)` — null 반환 조건: 국내 항목, 번역이 비어 있음, 500자 초과, 번역 안 숫자열(`\d+`)이 원문 숫자열 집합에 없는 것이 하나라도 있음, 번역이 원문과 같음. 그 밖은 trim 한 번역
  - `void Article.applyAiAnalysis(ArticleTopic topic, String acceptedTranslatedTitle)` — `aiStatus = DONE`
  - `void Article.recordAiFailure()` — `topic = GENERAL`, `translatedTitle = null`, `aiAttemptCount + 1`, `aiAttemptCount >= MAXIMUM_AI_ATTEMPT_COUNT(3)` 이면 `FAILED`, 아니면 `PENDING` 유지

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void acceptsTranslationKeepingCveAndVersion() {
    assertThat(toAcceptedTranslation("Critical RCE in Next.js 15.2.3 (CVE-2026-5555)", OVERSEAS,
        "Next.js 15.2.3 원격 코드 실행 심각 취약점 (CVE-2026-5555)"))
        .isEqualTo("Next.js 15.2.3 원격 코드 실행 심각 취약점 (CVE-2026-5555)");
}
@Test void dropsTranslationWithDigitsNotInOriginal() {
    assertThat(toAcceptedTranslation("Ivanti patches flaws", OVERSEAS, "Ivanti, 취약점 3개 패치")).isNull();
}
@Test void dropsTranslationWithChangedVersion() {
    assertThat(toAcceptedTranslation("Apache Tomcat 10.1.34 fixes RCE", OVERSEAS, "Apache Tomcat 10.1.33 원격 코드 실행 수정")).isNull();
}
@Test void dropsTranslationForDomesticItem() {
    assertThat(toAcceptedTranslation("랜섬웨어 피해 증가", DOMESTIC, "랜섬웨어 피해 증가")).isNull();
}
@Test void dropsBlankOrSameAsOriginal() { /* "  " > null, 원문과 같은 문자열 > null */ }

@Test void appliesAnalysisAndMarksDone() { /* applyAiAnalysis(RANSOMWARE, "번역") > topic RANSOMWARE, translatedTitle "번역", DONE */ }
@Test void staysPendingAfterFirstAndSecondFailure() { /* recordAiFailure 2회 > PENDING, attempt 2, topic GENERAL */ }
@Test void marksFailedAfterThirdFailure() { /* recordAiFailure 3회 > FAILED, attempt 3, translatedTitle null */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*ArticleTitleAnalysisPolicyTest*' --tests '*ArticleAiStateTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 11: Anthropic 분석기 + AI 묶음 처리 + AI 스케줄러 + 배포 키 전달

**Files:**
- Modify: `server/SB-Domain-Advisory/build.gradle` (`implementation 'com.anthropic:anthropic-java:<Maven Central 최신 안정 버전>'` — 구현자가 Maven Central 에서 확인해 고정)
- Modify: `ErrorCode.java` (외부 연동 구역에 `AI_API_ERROR(HttpStatus.BAD_GATEWAY, "AI 처리에 문제가 있어요.")`)
- Create: `.../advisory/ai/{ArticleTitleAnalyzer,ArticleTitleAnalysisPrompt,AnthropicArticleTitleAnalyzer}.java`
- Create: `server/SB-Application/src/main/java/kr/suhsaechan/secbrief/application/ai/{ArticleAiProcessService,ArticleAiResultRecorder}.java`
- Create: `server/SB-Web/src/main/java/kr/suhsaechan/secbrief/web/scheduler/ArticleAiScheduler.java`
- Modify: `server/SB-Web/src/main/resources/application.yml` (`secbrief.ai.enabled: ${AI_ENABLED:true}`, `secbrief.ai.anthropic-api-key: ${ANTHROPIC_API_KEY:}`, `secbrief.ai.model: claude-haiku-5-5`, `secbrief.ai.batch-size: 20`, `secbrief.ai.tick-milliseconds: 300000`, `secbrief.ai.initial-delay-milliseconds: 90000` + 값 뜻 주석)
- Modify: `.github/workflows/PROJECT-SPRING-SYNOLOGY-SECBRIEF-CICD.yaml` (SSH 배포 단계 `env` 에 `ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}`, `envs:` 목록에 `ANTHROPIC_API_KEY` 추가, `docker run` 에 `-e "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}"`)
- Test: `server/SB-Domain-Advisory/src/test/java/kr/suhsaechan/secbrief/advisory/ai/ArticleTitleAnalysisPromptTest.java`, `server/SB-Application/src/test/java/kr/suhsaechan/secbrief/application/ai/{ArticleAiProcessServiceTest,FakeArticleTitleAnalyzer}.java`

**Interfaces:**
- Consumes: Task 10 레코드·규칙·엔티티 전이, Task 1 `ArticleRepository.findByAiStatusOrderByIdAsc`, Task 9 `Clock`
- Produces:
  - `interface ArticleTitleAnalyzer { boolean isAvailable(); ArticleTitleAnalysis analyze(ArticleTitleAnalysisRequest request); }` — 실패는 `CustomException(AI_API_ERROR, ...)`
  - `final class ArticleTitleAnalysisPrompt`: `static String toUserMessage(ArticleTitleAnalysisRequest request)`, 상수 `SYSTEM_INSTRUCTION`, `static Map<String, Object> toOutputJsonSchema()` (`topic`: 8개 `apiValue` enum, 필수 / `translatedTitle`: string 또는 null), `static ArticleTitleAnalysis toAnalysis(String responseJson)` — JSON 아님·모르는 topic > `CustomException(AI_API_ERROR)`
  - `@Component AnthropicArticleTitleAnalyzer implements ArticleTitleAnalyzer` — `isAvailable()` = 키가 비어 있지 않음. 키가 없으면 SDK 클라이언트를 만들지 않음(서버 기동 영향 없음). 모델 `secbrief.ai.model`, 최대 출력 토큰 256, 구조화 출력(JSON 스키마) 사용. **SDK 클래스·메서드 이름은 `anthropic-java` README 와 javadoc 의 structured outputs 예제를 보고 쓴다 (기억으로 쓰지 않음).** 이 클래스는 단위 테스트하지 않고 Step 6 수동 1회 호출로 확인
  - `@Service ArticleAiProcessService(ArticleTitleAnalyzer analyzer, ArticleRepository articleRepository, ArticleAiResultRecorder recorder, @Value batchSize)`: `AiProcessResult processPendingArticles()` — `record AiProcessResult(int doneCount, int failedCount, boolean isSkipped)`
  - `@Component ArticleAiResultRecorder`: `@Transactional void recordSuccess(Long articleId, ArticleTitleAnalysis analysis)` (`toAcceptedTranslation` 거친 뒤 `applyAiAnalysis`), `@Transactional void recordFailure(Long articleId)` — API 호출 동안 트랜잭션을 잡지 않음
  - `@Component @ConditionalOnProperty(name = "secbrief.ai.enabled", havingValue = "true", matchIfMissing = true) ArticleAiScheduler` — `@Scheduled(fixedDelayString = "${secbrief.ai.tick-milliseconds}", initialDelayString = "${secbrief.ai.initial-delay-milliseconds}") void processPendingArticles()`

프롬프트 지시 (`SYSTEM_INSTRUCTION`, 영어로 작성): 보안 소식 제목 1개를 받아 주제 8종 중 하나를 고른다(각 주제 한 줄 정의: supplyChain = 오픈소스 패키지·빌드·배포 경로 공격, policy = 법·규제·과징금·정부 지침 등). `region` 이 OVERSEAS 면 한국어 제목을 만들고, CVE ID·버전 숫자·제품명·회사명은 원문 그대로 둔다. 원문에 없는 정보·숫자를 더하지 않는다. DOMESTIC 이면 `translatedTitle` 은 null.

`processPendingArticles` 흐름: 분석기 사용 불가면 로그 `[article ai] skipped : api key missing` 후 `isSkipped=true` 반환 > PENDING 을 `Limit.of(batchSize)` 로 조회 > 건별 `analyze` > 성공 `recordSuccess` / 예외 `recordFailure` (건별 격리) > 로그 `[article ai] processed : done={n}, failed={m}`. 기사 제목은 로그에 넣지 않는다.

- [ ] **Step 1: 실패 테스트 작성** (`FakeArticleTitleAnalyzer` 는 제목별 응답·예외를 미리 정해 두는 테스트 구현)

```java
// ArticleTitleAnalysisPromptTest
@Test void parsesStructuredResponse() {
    assertThat(toAnalysis("{\"topic\":\"supplyChain\",\"translatedTitle\":\"npm 패키지 악성코드\"}"))
        .isEqualTo(new ArticleTitleAnalysis(ArticleTopic.SUPPLY_CHAIN, "npm 패키지 악성코드"));
}
@Test void parsesNullTranslation() { /* {"topic":"policy","translatedTitle":null} > (POLICY, null) */ }
@Test void rejectsUnknownTopic() {
    assertThatThrownBy(() -> toAnalysis("{\"topic\":\"malware\"}")).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
}
@Test void rejectsNonJson() { /* "sorry" > AI_API_ERROR */ }
@Test void schemaListsAllEightTopics() {
    assertThat(topicEnumValues(toOutputJsonSchema())).containsExactlyInAnyOrder(
        "vulnerability", "ransomware", "privacy", "remoteAccess", "database", "supplyChain", "policy", "general");
}
@Test void userMessageContainsTitleAndRegionOnly() { /* toUserMessage 결과에 제목과 OVERSEAS 가 있고 그 밖의 필드 없음 */ }

// ArticleAiProcessServiceTest (Mockito 저장소 + Fake 분석기)
@Test void skipsWhenApiKeyMissing() {
    fakeAnalyzer.setAvailable(false);
    assertThat(service.processPendingArticles().isSkipped()).isTrue();
    verifyNoInteractions(articleRepository);
}
@Test void recordsSuccessForEachPendingArticle() { /* PENDING 2건 > recordSuccess 2회, doneCount 2 */ }
@Test void failureOfOneArticleDoesNotStopOthers() { /* 1건 예외, 1건 성공 > recordFailure 1, recordSuccess 1 */ }
@Test void readsAtMostBatchSize() { /* findByAiStatusOrderByIdAsc(PENDING, Limit.of(20)) 호출 확인 */ }
```

추가로 `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/advisory/ArticleAiResultRecorderTest.java` (Testcontainers):

```java
@Test void dropsInventedNumberButKeepsTopic() {
    // 해외 기사 "Ivanti patches flaws", 분석 (VULNERABILITY, "Ivanti 취약점 3개 패치") > topic VULNERABILITY, translatedTitle null, DONE
}
@Test void marksFailedAfterThirdFailure() { /* recordFailure 3회 > FAILED, 다음 findByAiStatus(PENDING) 에 안 나옴 */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*ArticleTitleAnalysisPromptTest*'`, `./gradlew :SB-Application:test --tests '*ArticleAiProcessServiceTest*'`, `./gradlew :SB-Web:test --tests '*ArticleAiResultRecorderTest*'` → FAIL
- [ ] **Step 3: 구현** (SDK README 확인 후 `AnthropicArticleTitleAnalyzer` 작성, 워크플로 3곳 수정)
- [ ] **Step 4: 통과 확인** — 위 명령 PASS, `./gradlew compileJava` → BUILD SUCCESSFUL. 테스트에서 실제 호출이 없는지: `application-test.yml` 의 `secbrief.ai.enabled: false`·빈 키 확인
- [ ] **Step 5: 키 없이 기동 확인** — Task 9 Step 5 서버를 `ANTHROPIC_API_KEY` 없이 띄워 2분 뒤 로그에 `[article ai] skipped : api key missing` 이 5분마다 나오고 기동·수집은 정상
- [ ] **Step 6: 실제 1회 호출 확인 (사용자가 키를 줄 때만)** — 사용자가 직접 `export ANTHROPIC_API_KEY=...` 한 셸에서 서버 기동 > 5분 뒤 `docker exec secbrief-pg psql -U secbrief -c "select ai_status, count(*) from article group by ai_status"` → DONE 이 늘어남, `select title, translated_title, topic from article where region='OVERSEAS' and translated_title is not null limit 5` 로 번역·숫자 보존 눈으로 확인. 키를 받지 못했으면 "안 돌림 (키 없음)"으로 보고
- [ ] **Step 7: 커밋** — `/pro-commit`

### Task 12: 조회 서비스 (오늘·목록 커서·주간 동향·출처)

**Files:**
- Modify: `ErrorCode.java` (요청 형식 구역에 `INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록 위치 값이 올바르지 않아요.")`)
- Create: `.../advisory/service/{ArticleCursor,ArticleSpecifications,ArticleQueryService,TodayQueryService,WeeklyTrendQueryService,SourceQueryService}.java`
- Create: `.../advisory/dto/{ArticleResponse,ArticlePageResponse,ArticleSearchCondition,TodayResponse,TrendTopicResponse,WeeklyTrendResponse,SourceStatusResponse}.java`
- Modify: `ArticleRepository` (`long countByPublishedAtGreaterThanEqual(Instant start)`, `@Query("select new ...TopicCount(a.topic, count(a)) from Article a where a.publishedAt >= :start and a.publishedAt < :end group by a.topic") List<TopicCount> countByTopicBetween(Instant start, Instant end)`, `record TopicCount(ArticleTopic topic, long count)`)
- Test: `.../advisory/service/{ArticleCursorTest,WeeklyTrendCalculationTest}.java` (단위), `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/advisory/{ArticleQueryServiceTest,TodayAndTrendQueryServiceTest}.java` (Testcontainers + 고정 `Clock` `@TestConfiguration`)

**Interfaces:**
- Consumes: Task 1 저장소, Task 3 `SeoulCalendar`, Task 9 `Clock` 빈
- Produces:
  - `record ArticleResponse(Long id, ArticleKind kind, ArticleRegion region, ArticleTopic topic, String title /* 원문 */, String translatedTitle /* null 허용 */, String originalUrl, String publisherName, Instant publishedAt, boolean isPublishedAtEstimated, String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev, boolean isUrgent)` + `static ArticleResponse from(Article article)` (`aiStatus` 는 내보내지 않음)
  - `record ArticlePageResponse(List<ArticleResponse> items, String nextCursor /* 끝이면 null */)`
  - `record ArticleSearchCondition(ArticleKind kind, ArticleRegion region, ArticleTopic topic, String cursor, Integer size)` — `size` null 이면 `DEFAULT_PAGE_SIZE = 20`, 1~`MAXIMUM_PAGE_SIZE = 50` 밖이면 `CustomException(INVALID_REQUEST)`
  - `record ArticleCursor(Instant publishedAt, long id)` + `static String encode(Instant publishedAt, long id)` (URL-safe Base64, 패딩 없음, 원문 `"{epochMilli}:{id}"`) + `static ArticleCursor decode(String cursor)` (깨지면 `CustomException(INVALID_CURSOR)`)
  - `ArticleQueryService.findArticles(ArticleSearchCondition condition): ArticlePageResponse` — `ArticleSpecifications` 로 null 아닌 필터만 + 커서 조건 `publishedAt < c.publishedAt or (publishedAt = c.publishedAt and id < c.id)`, 정렬 `publishedAt desc, id desc`, `articleRepository.findBy(spec, query -> query.sortBy(sort).limit(size + 1).all())` 로 한 건 더 읽어 다음 커서 판정 (count 쿼리 없음). AI 상태와 무관하게 모두 반환
  - `record TodayResponse(LocalDate date, ArticleResponse urgentItem /* null 허용 */, long todayCount, long weeklyCount, TrendTopicResponse topTrend /* null 허용 */, List<ArticleResponse> streamItems)`
  - `TodayQueryService.findToday(): TodayResponse` — 긴급: `isUrgent && publishedAt >= now - 72h` 중 최신 1건 (`URGENT_VISIBLE_DURATION = Duration.ofHours(72)`). 스트림: 최신 `TODAY_STREAM_SIZE = 20`건(긴급 항목 제외). `todayCount`·`weeklyCount`: `startOfToday`·`startOfWeek` 이후 게시 건수. `topTrend`: 주간 동향 중 `GENERAL` 제외, 건수 > 0 인 첫 항목
  - `record TrendTopicResponse(ArticleTopic topic, long weeklyCount, long previousWeekCount, Integer changePercent /* 지난주 0 이면 null */)`, `record WeeklyTrendResponse(LocalDate weekStartDate, List<TrendTopicResponse> topics)` — 8종 모두(0건 포함), 이번 주 건수 내림차순, 같으면 enum 순서
  - `WeeklyTrendQueryService.findWeeklyTrend(): WeeklyTrendResponse` — 이번 주 `[startOfWeek, now)` vs 지난주 같은 경과 구간 `[startOfWeek - 7일, now - 7일)`
  - `static Integer WeeklyTrendQueryService.toChangePercent(long weeklyCount, long previousWeekCount)` — `Math.round((weekly - previous) * 100.0 / previous)`, `previous == 0` 이면 null
  - `record SourceStatusResponse(String code, String displayName, ArticleKind kind, ArticleRegion region, Instant lastCollectedAt, CollectStatus lastStatus)`, `SourceQueryService.findEnabledSources(): List<SourceStatusResponse>`

- [ ] **Step 1: 실패 테스트 작성**

```java
// ArticleCursorTest
@Test void roundTripsCursor() {
    Instant publishedAt = Instant.parse("2026-11-23T01:02:03Z");
    assertThat(decode(encode(publishedAt, 42))).isEqualTo(new ArticleCursor(publishedAt, 42));
}
@Test void rejectsGarbageCursorWithInvalidCursorCode() {
    assertThatThrownBy(() -> decode("not-a-cursor")).extracting("errorCode").isEqualTo(ErrorCode.INVALID_CURSOR);
}
// WeeklyTrendCalculationTest
@Test void changePercentRoundsAndHandlesZeroPrevious() {
    assertThat(toChangePercent(12, 10)).isEqualTo(20);
    assertThat(toChangePercent(5, 10)).isEqualTo(-50);
    assertThat(toChangePercent(3, 0)).isNull();
}
// ArticleQueryServiceTest
@Test void pagesNewestFirstWithoutGapsOrDuplicates() {
    // 같은 publishedAt 3건 포함 25건 > size 10 으로 3페이지 > id 25개 각 1번, 마지막 nextCursor null
}
@Test void filtersByKindAndRegion() { /* NEWS·DOMESTIC 만 반환 */ }
@Test void filtersByTopic() { /* SUPPLY_CHAIN 만 반환 */ }
@Test void includesPendingAndFailedAiArticles() { /* PENDING·FAILED 기사도 원문 제목으로 반환, translatedTitle null */ }
@Test void rejectsSizeAboveFifty() { /* size 51 > CustomException INVALID_REQUEST */ }
// TodayAndTrendQueryServiceTest
@Test void todayCountsUseSeoulMidnight() {
    // now = 2026-11-23T16:30Z(11-24 01:30 KST), 게시 2026-11-23T15:10Z 1건, 2026-11-23T14:50Z 1건 > todayCount 1
}
@Test void urgentItemExpiresAfterSeventyTwoHours() { /* 73시간 전 긴급 1건만 > urgentItem null, streamItems 엔 포함 */ }
@Test void streamExcludesUrgentItem() { /* 긴급 1건 + 일반 2건 > streamItems 2건 */ }
@Test void topTrendSkipsGeneral() { /* GENERAL 5건, RANSOMWARE 2건 > topTrend.topic RANSOMWARE */ }
@Test void weeklyTrendComparesSameElapsedSpanOfPreviousWeek() {
    // now 수요일 12:00 KST: 이번 주 월~수12시 RANSOMWARE 3건, 지난주 월~수12시 2건 + 지난주 목 1건 > weekly 3, previous 2, changePercent 50
}
@Test void weeklyTrendListsAllEightTopics() { /* 데이터 없음 > topics 8개, 모두 0, changePercent null */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Domain-Advisory:test --tests '*ArticleCursorTest*' --tests '*WeeklyTrendCalculationTest*'` 와 `./gradlew :SB-Web:test --tests '*ArticleQueryServiceTest*' --tests '*TodayAndTrendQueryServiceTest*'` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 13: 읽기 API 4개

**Files:**
- Create: `server/SB-Web/src/main/java/kr/suhsaechan/secbrief/web/controller/{TodayController,ArticleController,TrendController,SourceController}.java`
- Test: `server/SB-Web/src/test/java/kr/suhsaechan/secbrief/web/controller/AdvisoryApiControllerTest.java` (`@WebMvcTest({TodayController.class, ArticleController.class, TrendController.class, SourceController.class})`, 서비스는 `@MockitoBean`)

**Interfaces:**
- Consumes: Task 12 서비스·DTO
- Produces (모두 `ApiResponse<...>`, 인증 없음):
  - `GET /api/v1/today` → `TodayResponse`
  - `GET /api/v1/articles?kind=&region=&topic=&cursor=&size=` → `ArticlePageResponse` (enum 은 대문자 이름, 틀리면 기존 핸들러가 `TYPE_MISMATCH`)
  - `GET /api/v1/trends/weekly` → `WeeklyTrendResponse`
  - `GET /api/v1/sources` → `List<SourceStatusResponse>`

- [ ] **Step 1: 실패 테스트 작성**

```java
@Test void returnsArticlesWrappedInDataWithBooleanFieldNames() throws Exception {
    given(articleQueryService.findArticles(any())).willReturn(new ArticlePageResponse(List.of(nvdKevArticleResponse()), "c1"));
    mockMvc.perform(get("/api/v1/articles").param("kind", "VULNERABILITY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].publisherName").value("NVD"))
        .andExpect(jsonPath("$.data.items[0].title").value("CVE-2026-1111: Example remote code execution"))
        .andExpect(jsonPath("$.data.items[0].translatedTitle").value("CVE-2026-1111: 원격 코드 실행 예시"))
        .andExpect(jsonPath("$.data.items[0].isKev").value(true))
        .andExpect(jsonPath("$.data.items[0].isPublishedAtEstimated").value(false))
        .andExpect(jsonPath("$.data.items[0].publishedAt").value("2026-11-23T00:00:00Z"))
        .andExpect(jsonPath("$.data.items[0].aiStatus").doesNotExist())
        .andExpect(jsonPath("$.data.nextCursor").value("c1"));
}
@Test void returnsPendingArticleWithOriginalTitle() { /* translatedTitle null, topic GENERAL 인 응답 > $.data.items[0].translatedTitle null */ }
@Test void passesFiltersToService() { /* kind=NEWS&region=DOMESTIC&topic=SUPPLY_CHAIN&size=5 > ArgumentCaptor 로 조건 확인 */ }
@Test void rejectsUnknownKindWithTypeMismatch() throws Exception {
    mockMvc.perform(get("/api/v1/articles").param("kind", "BLOG"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
}
@Test void returnsInvalidCursorErrorCode() throws Exception { /* 서비스가 CustomException(INVALID_CURSOR) > 400, $.errorCode INVALID_CURSOR */ }
@Test void returnsTodayWithNullUrgentItem() throws Exception { /* $.data.urgentItem null, $.data.todayCount 숫자 */ }
@Test void returnsWeeklyTrendTopicsAsEnumNames() throws Exception { /* $.data.topics[0].topic == "SUPPLY_CHAIN" */ }
@Test void returnsSources() throws Exception { /* $.data[0].lastStatus == "SUCCESS" */ }
```

- [ ] **Step 2: 실패 확인** — `./gradlew :SB-Web:test --tests '*AdvisoryApiControllerTest*'` → FAIL
- [ ] **Step 3: 구현** — `ArticleController` 는 `@RequestParam(required = false)` 5개를 받아 `ArticleSearchCondition` 으로 묶음
- [ ] **Step 4: 통과 확인** → PASS. 이어서 `./gradlew compileJava` → BUILD SUCCESSFUL, `./gradlew test` → 전부 PASS (Docker 켠 상태)
- [ ] **Step 5: 로컬 API 실측** — Task 9 Step 5 서버로:
  - `curl -s localhost:8080/api/v1/today | jq '.data | {todayCount, weeklyCount, urgent: .urgentItem.title, stream: (.streamItems | length)}'`
  - `curl -s 'localhost:8080/api/v1/articles?kind=NEWS&region=OVERSEAS&size=3' | jq '.data.items[] | {title, translatedTitle, publisherName, publishedAt}'`
  - `curl -s localhost:8080/api/v1/sources | jq '.data[] | {code, lastStatus}'` → 7개
  - 같은 CVE 중복 없음: `curl -s 'localhost:8080/api/v1/articles?kind=VULNERABILITY&size=50' | jq '[.data.items[].cveId | select(. != null)] | group_by(.) | map(select(length>1))'` → `[]`
  - 컨테이너는 앱 실화면 확인(Task 18~22)까지 두고, Task 22 끝에 `docker rm -f secbrief-pg` (이번 작업이 만든 컨테이너)
- [ ] **Step 6: 커밋** — `/pro-commit`

---

## 앱

모든 앱 Task 의 공통 검증 (`cd mobile`): `npx tsc --noEmit` → 출력 없음, `npx eslint .` → 오류 0 (mobile/CLAUDE.md: `expo lint` 는 ESLint 9 를 거부해 직접 실행), `npx jest` → 전부 PASS.

### Task 14: API 클라이언트 + 에러 코드 변환 + jest 경로 설정

**Files:**
- Modify: `mobile/package.json` (jest 에 `"moduleNameMapper": { "^@/(.*)$": "<rootDir>/src/$1" }`)
- Modify: `mobile/src/core/error/app-failure.ts`
- Create: `mobile/src/data/sources/secbrief-api-client.ts`
- Test: `mobile/tests/data/secbrief-api-client.test.ts`

**Interfaces:**
- Consumes: `apiConfig.baseUrl`, `apiConfig.requestTimeoutMilliseconds`, `AppFailure`
- Produces:
  - `app-failure.ts` 상수: `NETWORK_ERROR_CODE = 'APP_NETWORK'`, `TIMEOUT_ERROR_CODE = 'APP_TIMEOUT'`, `INVALID_RESPONSE_ERROR_CODE = 'APP_INVALID_RESPONSE'`
  - `fetchApiData<T>(path: string, queryParameters?: Record<string, string | undefined>): Promise<T>` — `undefined` 값은 쿼리에서 뺌, `AbortController` 로 시간 제한, 2xx 이고 `body.data` 가 있으면 반환
  - 실패 변환: 시간 초과 > `AppFailure('APP_TIMEOUT', '응답이 늦어요. 잠시 후 다시 시도해 주세요.')`, 네트워크 > `AppFailure('APP_NETWORK', '인터넷 연결을 확인해 주세요.')`, JSON 아님·`data` 없음 > `AppFailure('APP_INVALID_RESPONSE', '서버 응답을 읽지 못했어요.')`, 서버 오류 본문 `{errorCode, message}` > `AppFailure(errorCode, message)`, 오류 본문이 없으면 `AppFailure('HTTP_' + status, '서버에 문제가 생겼어요.')`

- [ ] **Step 1: 실패 테스트 작성** (`global.fetch = jest.fn()`, 응답 헬퍼 `mockFetchOnce(status, jsonBody)`, `mockFetchTextOnce(status, text)`)

```ts
test('성공 응답의 data 만 돌려준다', async () => {
  mockFetchOnce(200, { data: { value: 1 } });
  await expect(fetchApiData('/api/v1/today')).resolves.toEqual({ value: 1 });
});
test('undefined 쿼리 값은 주소에서 뺀다', async () => {
  mockFetchOnce(200, { data: [] });
  await fetchApiData('/api/v1/articles', { kind: 'NEWS', region: undefined });
  expect((global.fetch as jest.Mock).mock.calls[0][0]).toBe(`${apiConfig.baseUrl}/api/v1/articles?kind=NEWS`);
});
test('서버 에러 본문의 errorCode 를 그대로 쓴다', async () => {
  mockFetchOnce(400, { errorCode: 'INVALID_CURSOR', message: '목록 위치 값이 올바르지 않아요.' });
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'INVALID_CURSOR', userMessage: '목록 위치 값이 올바르지 않아요.' });
});
test('rejectsNonJsonBodyWithInvalidResponseCode', async () => {
  mockFetchTextOnce(200, '<html>proxy error</html>');
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'APP_INVALID_RESPONSE' });
});
test('오류 본문 없는 502 는 HTTP_502', async () => {
  mockFetchTextOnce(502, '');
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'HTTP_502' });
});
test('네트워크 실패는 APP_NETWORK', async () => {
  (global.fetch as jest.Mock).mockRejectedValueOnce(new TypeError('Network request failed'));
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'APP_NETWORK' });
});
test('시간 제한을 넘기면 APP_TIMEOUT', async () => {
  // jest.useFakeTimers(), fetch 는 signal abort 때 DOMException('AbortError') 로 reject > 시간 제한만큼 진행 > 'APP_TIMEOUT'
});
```

- [ ] **Step 2: 실패 확인** — `npx jest tests/data/secbrief-api-client.test.ts` → FAIL (모듈 없음)
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 공통 검증 3개 PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 15: 모델·응답 변환·날짜 표기 + 샘플 소스 제거 + 규칙 테스트

**Files:**
- Modify: `mobile/src/data/models/{briefing-item,today-briefing,trend-topic}.ts`
- Create: `mobile/src/data/models/{article-page,collect-source-status}.ts`, `mobile/src/data/mappers/api-response-mapper.ts`
- Create: `mobile/src/data/sources/{today-briefing-source,article-source,weekly-trend-source,collect-source-status-source}.ts`
- Delete: `mobile/src/data/sources/sample-today-briefing-source.ts` (스펙 §9 교체·반목표로 정해진 삭제)
- Modify: `mobile/src/core/format/briefing-date-formatter.ts`
- Modify: `mobile/src/features/today/{today-view-state.ts,use-today-briefing.ts}` (컴파일 유지용 최소 수정, 본 작업은 Task 18)
- Modify: `mobile/tests/architecture/architecture-rules.test.ts`
- Test: `mobile/tests/data/api-response-mapper.test.ts`, `mobile/tests/core/briefing-date-formatter.test.ts`

**Interfaces:**
- Consumes: Task 14 `fetchApiData`, Task 13 응답 형태
- Produces:
  - `type BriefingTopic = 'vulnerability' | 'ransomware' | 'privacy' | 'remoteAccess' | 'database' | 'supplyChain' | 'policy' | 'general'` (주석에 8종 뜻)
  - `type ArticleKind = 'news' | 'advisory' | 'vulnerability'`, `type ArticleRegion = 'domestic' | 'overseas'`
  - `interface BriefingItem { id: string; kind: ArticleKind; region: ArticleRegion; topic: BriefingTopic; title: string /* 원문 */; translatedTitle?: string /* AI 한국어 제목 */; originalUrl: string; publisherName: string; publishedAt: Date; isPublishedAtEstimated: boolean; cveId?: string; cvssScore?: number; cvssVersion?: string; isKev: boolean; isUrgent: boolean }` (`sourceName`·`productName`·`isMyStackMatch` 제거 — 회원 기능 범위 밖, 지어낸 값 금지)
  - `interface TodayBriefing { date: Date; todayCount: number; weeklyCount: number; urgentItem?: BriefingItem; topTrend?: TrendTopic; streamItems: BriefingItem[] }` (`readCount`·`totalCount`·`streakDays` 제거), `isTodayBriefingEmpty` 유지
  - `interface TrendTopic { topic: BriefingTopic; weeklyCount: number; previousWeekCount: number; changePercent?: number }` (`name` 제거 — 표시 이름은 Task 17 `toTopicLabel`)
  - `interface ArticlePage { items: BriefingItem[]; nextCursor?: string }`, `interface WeeklyTrend { weekStartDate: Date; topics: TrendTopic[] }`, `interface ArticleFilter { kind?: ArticleKind; region?: ArticleRegion; topic?: BriefingTopic }`
  - `interface CollectSourceStatus { code: string; displayName: string; kind: ArticleKind; region: ArticleRegion; lastCollectedAt?: Date; isLastCollectSucceeded: boolean }`
  - mapper (응답 타입 `ArticleApiResponse` 등은 mapper 파일 안에 정의): `toBriefingItem`, `toTodayBriefing`, `toWeeklyTrend`, `toArticlePage`, `toCollectSourceStatus`, `toApiTopic(topic: BriefingTopic): string`(`supplyChain` > `SUPPLY_CHAIN`), `toApiKind`, `toApiRegion`. 모르는 주제 > `general`, 모르는 종류 > `news`, 모르는 지역 > `overseas`. 빈 문자열 `translatedTitle` 은 undefined
  - sources: `fetchTodayBriefing(): Promise<TodayBriefing>`, `fetchArticlePage(filter: ArticleFilter, cursor?: string): Promise<ArticlePage>`, `fetchWeeklyTrend(): Promise<WeeklyTrend>`, `fetchCollectSourceStatuses(): Promise<CollectSourceStatus[]>`
  - formatter: `toArticleDateLabel(publishedAt: Date, now: Date): string` → `'2026.11.23 09:12 · 2시간 전'` (상대: 1분 미만·미래 `방금`, 60분 미만 `N분 전`, 24시간 미만 `N시간 전`, 그 외 `N일 전`)

- [ ] **Step 1: 실패 테스트 작성**

```ts
// api-response-mapper.test.ts
test('mapsUnknownTopicToGeneral', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'MALWARE' }).topic).toBe('general');
});
test('새 주제 SUPPLY_CHAIN·POLICY 를 받는다', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'SUPPLY_CHAIN' }).topic).toBe('supplyChain');
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'POLICY' }).topic).toBe('policy');
});
test('원문 제목과 번역 제목을 따로 둔다', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' });
  expect(item.title).toBe('Critical flaw in Next.js');
  expect(item.translatedTitle).toBe('Next.js 심각 취약점');
  expect(toBriefingItem({ ...sampleArticleResponse, translatedTitle: null }).translatedTitle).toBeUndefined();
});
test('REMOTE_ACCESS 는 remoteAccess, 날짜는 Date', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, topic: 'REMOTE_ACCESS', publishedAt: '2026-11-23T00:00:00Z' });
  expect(item.topic).toBe('remoteAccess');
  expect(item.publishedAt.toISOString()).toBe('2026-11-23T00:00:00.000Z');
});
test('null cvss·cve 는 undefined', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, cvssScore: null, cveId: null });
  expect(item.cvssScore).toBeUndefined();
  expect(item.cveId).toBeUndefined();
});
test('nextCursor null 은 undefined', () => { expect(toArticlePage({ items: [], nextCursor: null }).nextCursor).toBeUndefined(); });
test('lastStatus SUCCESS 만 성공', () => {
  expect(toCollectSourceStatus({ ...sampleSourceResponse, lastStatus: 'FAILED' }).isLastCollectSucceeded).toBe(false);
  expect(toCollectSourceStatus({ ...sampleSourceResponse, lastCollectedAt: null }).lastCollectedAt).toBeUndefined();
});
test('toApiTopic 은 서버 enum 이름', () => { expect(toApiTopic('supplyChain')).toBe('SUPPLY_CHAIN'); });

// briefing-date-formatter.test.ts (기기 시간대 기준 — 테스트는 로컬 Date 생성자로 만든다)
const NOW = new Date(2026, 10, 23, 12, 0);
test('절대 날짜 + 시간 전', () => { expect(toArticleDateLabel(new Date(2026, 10, 23, 9, 12), NOW)).toBe('2026.11.23 09:12 · 2시간 전'); });
test('1분 미만은 방금', () => { expect(toArticleDateLabel(new Date(2026, 10, 23, 11, 59, 30), NOW)).toBe('2026.11.23 11:59 · 방금'); });
test('하루 넘으면 일 전', () => { expect(toArticleDateLabel(new Date(2026, 10, 20, 12, 0), NOW)).toBe('2026.11.20 12:00 · 3일 전'); });
test('미래 시각은 방금', () => { expect(toArticleDateLabel(new Date(2026, 10, 23, 13, 0), NOW)).toBe('2026.11.23 13:00 · 방금'); });
```

규칙 테스트 추가 (`architecture-rules.test.ts`):

```ts
describe('운영 빌드에 샘플 데이터 금지', () => {
  test('src 아래에 sample 파일이 없다', () => {
    expect(listSourceFiles('').filter((file) => /(^|\/)sample-/.test(file))).toEqual([]);
  });
  test('src 어디에서도 sample 모듈을 import 하지 않는다', () => {
    expect(listSourceFiles('').filter((file) => /from '[^']*sample-[^']*'/.test(readSource(file)))).toEqual([]);
  });
});
describe('외부 통신은 API 클라이언트 한 곳', () => {
  test('fetch( 는 data/sources/secbrief-api-client.ts 에만 있다', () => {
    expect(listSourceFiles('').filter((file) => /\bfetch\(/.test(readSource(file))))
      .toEqual([path.join('data', 'sources', 'secbrief-api-client.ts')]);
  });
});
```

- [ ] **Step 2: 실패 확인** — `npx jest` → 새 테스트·샘플 규칙 FAIL
- [ ] **Step 3: 구현** — 모델·mapper·source·formatter 작성, 샘플 소스 삭제. 컴파일 유지를 위해 `use-today-briefing.ts` 의 `queryFn` 을 `fetchTodayBriefing` 으로, `today-view-state.ts` 는 제거된 필드 참조만 최소 수정(머리 둘째 줄 `오늘 ${todayCount}건`, 연속 확인 자리는 `weeklyCount`, `COVER_KIND_BY_TOPIC` 에 새 3종 추가). 화면 문구·누름 동작은 Task 18
- [ ] **Step 4: 통과 확인** — 공통 검증 3개 PASS
- [ ] **Step 5: 커밋** — `/pro-commit` (삭제 파일 포함, 경로 명시)

### Task 16: 디자인 시스템 목록 행·설정 행·필터 칩

**Files:**
- Create: `mobile/src/design/components/{sb-article-row,sb-list-row,sb-filter-chips}.tsx`
- Modify: `mobile/src/design/index.ts` (export 3줄), 필요한 간격·모서리 값이 없을 때만 `mobile/src/design/tokens/{sb-spacing,sb-radius}.ts`

**Interfaces:**
- Consumes: `SbText`, `SbPressable`, `useSbTheme`, `useSbStyles`, 토큰, `SbIconName`
- Produces:
  - `SbArticleRow(props: { title: string; originalTitle?: string; translationNoticeLabel?: string; metaLabel: string; badgeLabels: string[]; accessibilityLabel: string; onPress: () => void })` — 제목(`streamTitle`, 최대 3줄, 크게) > `originalTitle` 있으면 원문 제목(`caption`, `secondaryLabel` 색, 최대 2줄, 작게) > 메타(`timeLabel`) > 배지 줄(`capsLabel`, `translationNoticeLabel` 이 있으면 배지 맨 앞에 강조색) > 아래 구분선. 문구는 화면이 넘김(디자인 계층에 한국어 문구를 두지 않음)
  - `SbListRow(props: { title: string; detail?: string; trailingIconName?: SbIconName; accessibilityLabel: string; onPress?: () => void })` — `onPress` 없으면 누름 반응 없음
  - `SbFilterChips<Value extends string>(props: { options: { value: Value; label: string }[]; selectedValue: Value; onSelected: (value: Value) => void })` — 선택 칩 `accent` 배경 + `onAccent` 글자, `accessibilityRole="button"`, `accessibilityState={{ selected }}`

- [ ] **Step 1: 구현** (컴포넌트 렌더 테스트 기반이 없으므로 규칙 테스트·타입·린트·실화면이 검증)
- [ ] **Step 2: 검증** — 공통 검증 3개 PASS
- [ ] **Step 3: 커밋** — `/pro-commit`

### Task 17: 탭 공통 (항목 표시·원문 열기·당겨서 새로고침·동향 문구)

**Files:**
- Create: `mobile/src/features/articles/{article-row-view,open-original-article,use-pull-refresh}.ts`
- Create: `mobile/src/features/trend/trend-view-state.ts`
- Test: `mobile/tests/features/{article-row-view,trend-view-state}.test.ts`

**Interfaces:**
- Consumes: Task 15 모델·`toArticleDateLabel`, Task 16 props 형태
- Produces:
  - `toTopicLabel(topic: BriefingTopic): string` — 취약점, 랜섬웨어, 개인정보, 원격 접속, 데이터베이스, 공급망, 정책·규제, 일반
  - `toPublisherLabel(item: BriefingItem): string` — `isKev` 이고 게시자가 `CISA KEV` 가 아니면 `'{게시자} · CISA KEV 등재'`, 아니면 게시자
  - `toDisplayTitle(item: BriefingItem): string` — `translatedTitle ?? title`
  - `AI_TRANSLATION_NOTICE_LABEL = 'AI 번역'`
  - `interface ArticleRowView { id: string; title: string; originalTitle?: string; translationNoticeLabel?: string; metaLabel: string; badgeLabels: string[]; accessibilityLabel: string; originalUrl: string }`
  - `toArticleRowView(item: BriefingItem, now: Date): ArticleRowView` — 번역 있으면 `title = 번역`, `originalTitle = 원문`, `translationNoticeLabel = 'AI 번역'`; 없으면 `title = 원문`, 나머지 undefined. `metaLabel = '{toPublisherLabel} · {추정이면 "수집 "}{toArticleDateLabel}'`, `badgeLabels = [국내|해외, 뉴스|공지|취약점, 주제, (cvss) 'CVSS 9.8 (v3.1)', (긴급) '긴급']`, `accessibilityLabel = '{표시 제목}, {metaLabel}, 원문 열기'`
  - `openOriginalArticle(originalUrl: string): Promise<void>` — `WebBrowser.openBrowserAsync(originalUrl)`, 실패하면 `Linking.openURL(originalUrl)`
  - `usePullRefresh(refetch: () => Promise<unknown>): { isPullRefreshing: boolean; onPullRefresh: () => Promise<void> }` (오늘 화면 기존 로직을 옮김, 4개 탭이 씀)
  - `interface TrendTopicView { topic: BriefingTopic; topicLabel: string; detail: string }`, `toTrendTopicView(trend: TrendTopic): TrendTopicView` — `'12건 · 지난주보다 20% 늘었어요'` / `'… 줄었어요'` / `'12건 · 지난주와 같아요'` / `changePercent` 없음 `'3건 · 이번 주에 새로 나왔어요'` / 이번 주 0건이고 지난주도 0건 `'0건'`

- [ ] **Step 1: 실패 테스트 작성**

```ts
test('KEV 등재 NVD 항목은 게시자 옆에 등재 표시', () => {
  expect(toPublisherLabel({ ...nvdItem, isKev: true })).toBe('NVD · CISA KEV 등재');
  expect(toPublisherLabel({ ...kevOnlyItem, publisherName: 'CISA KEV', isKev: true })).toBe('CISA KEV');
});
test('번역 항목은 한국어 제목 + 원문 제목 + AI 번역 표시', () => {
  const view = toArticleRowView({ ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' }, NOW);
  expect(view.title).toBe('Next.js 심각 취약점');
  expect(view.originalTitle).toBe('Critical flaw in Next.js');
  expect(view.translationNoticeLabel).toBe('AI 번역');
});
test('번역 없으면 원문 제목만', () => {
  const view = toArticleRowView({ ...overseasNewsItem, translatedTitle: undefined }, NOW);
  expect(view.title).toBe(overseasNewsItem.title);
  expect(view.originalTitle).toBeUndefined();
  expect(view.translationNoticeLabel).toBeUndefined();
});
test('행 메타에 게시자와 절대·상대 날짜', () => {
  expect(toArticleRowView(kisaItem, NOW).metaLabel).toBe('KISA 보호나라 · 2026.11.23 09:12 · 2시간 전');
});
test('추정 게시일은 수집 표시', () => {
  expect(toArticleRowView({ ...kisaItem, isPublishedAtEstimated: true }, NOW).metaLabel).toBe('KISA 보호나라 · 수집 2026.11.23 09:12 · 2시간 전');
});
test('배지: 지역·종류·주제·CVSS·긴급', () => {
  expect(toArticleRowView(nvdItem, NOW).badgeLabels).toEqual(['해외', '취약점', '원격 접속', 'CVSS 9.8 (v3.1)', '긴급']);
});
test('새 주제 이름', () => {
  expect(toTopicLabel('supplyChain')).toBe('공급망');
  expect(toTopicLabel('policy')).toBe('정책·규제');
});
test('동향 문구 4종', () => {
  expect(toTrendTopicView({ topic: 'ransomware', weeklyCount: 12, previousWeekCount: 10, changePercent: 20 }).detail).toBe('12건 · 지난주보다 20% 늘었어요');
  expect(toTrendTopicView({ topic: 'privacy', weeklyCount: 5, previousWeekCount: 10, changePercent: -50 }).detail).toBe('5건 · 지난주보다 50% 줄었어요');
  expect(toTrendTopicView({ topic: 'database', weeklyCount: 4, previousWeekCount: 4, changePercent: 0 }).detail).toBe('4건 · 지난주와 같아요');
  expect(toTrendTopicView({ topic: 'general', weeklyCount: 3, previousWeekCount: 0 }).detail).toBe('3건 · 이번 주에 새로 나왔어요');
});
```

- [ ] **Step 2: 실패 확인** — `npx jest tests/features` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 공통 검증 3개 PASS
- [ ] **Step 5: 커밋** — `/pro-commit`

### Task 18: 오늘 탭 실데이터 연결

**Files:**
- Modify: `mobile/src/features/today/{today-view-state.ts,today-briefing-content.tsx,today-screen.tsx,use-today-briefing.ts}`
- Test: `mobile/tests/features/today-view-state.test.ts`

**Interfaces:**
- Consumes: Task 15 `fetchTodayBriefing`, Task 17 `toArticleRowView`·`toDisplayTitle`·`toPublisherLabel`·`toTrendTopicView`·`openOriginalArticle`·`usePullRefresh`·`AI_TRANSLATION_NOTICE_LABEL`
- Produces:
  - `TodayViewState { mastheadTrailingLines: string[]; weeklyCount: number; weeklyDetail: string; urgent?: TodayUrgentView; trend?: TodayTrendView; streamItems: TodayStreamItemView[]; isEmpty: boolean }` (`streakDays`·`streakDetail` 제거). `TodayUrgentView`·`TodayStreamItemView` 에 `originalUrl: string` 추가
  - `toTodayViewState(briefing: TodayBriefing, now: Date = new Date()): TodayViewState` — `select` 에서 쓸 때는 `(briefing) => toTodayViewState(briefing, new Date())`
  - `COVER_KIND_BY_TOPIC`: 기존 5종 + `supplyChain: 'cylinder'`, `policy: 'lock'`, `general: 'target'` (스펙: 기존 그림 재사용)
  - 기존 `toTrendView` 삭제 > `toTrendTopicView` 사용 (문구 단일 원본)

표시: 머리 오른쪽 `['11.23 MON', '오늘 12건']`. 둘째 통계 타일: 라벨 `이번 주`, 값 `{ countTarget: weeklyCount, countUnit: '건' }`, 설명 `이번 주에 모은 보안 소식이에요`. 긴급 타일·스트림 제목은 `toDisplayTitle`(한국어 우선). 긴급 타일 라벨: CVSS 있으면 `긴급 / CVSS 9.8`, 점수 없고 KEV 면 `긴급 / KEV 등재`, 그 외 `긴급`. 아래 줄 `'{toPublisherLabel} · {toArticleDateLabel}'` + 번역이면 `' · AI 번역'`. 스트림 `timeLabel` = `metaLabel` + 번역이면 `' · AI 번역'` (레일 항목은 원문 제목 줄이 없어 표시만 붙임). 긴급·스트림 누름 > `openOriginalArticle(originalUrl)`. 동향 1위 누름 > `router.navigate('/trend')`.

- [ ] **Step 1: 실패 테스트 작성**

```ts
test('머리 라벨은 날짜와 오늘 건수', () => {
  expect(toTodayViewState(briefing({ todayCount: 12 }), NOW).mastheadTrailingLines).toEqual(['11.23 MON', '오늘 12건']);
});
test('KEV 만 있는 긴급 항목 라벨', () => {
  expect(toTodayViewState(briefing({ urgentItem: kevOnlyItem }), NOW).urgent?.capsLabel).toBe('긴급 / KEV 등재');
});
test('긴급 아래 줄에 게시자와 날짜', () => {
  expect(toTodayViewState(briefing({ urgentItem: nvdKevItem }), NOW).urgent?.footerLabel).toBe('NVD · CISA KEV 등재 · 2026.11.23 09:12 · 2시간 전');
});
test('번역된 긴급 항목은 한국어 제목과 AI 번역 표시', () => {
  const urgent = toTodayViewState(briefing({ urgentItem: { ...nvdKevItem, translatedTitle: '원격 코드 실행 취약점' } }), NOW).urgent;
  expect(urgent?.title).toBe('원격 코드 실행 취약점');
  expect(urgent?.footerLabel).toMatch(/ · AI 번역$/);
});
test('새 주제도 커버 그림이 있다', () => {
  for (const topic of ['supplyChain', 'policy', 'general'] as const) {
    expect(toTodayViewState(briefing({ streamItems: [{ ...newsItem, topic }] }), NOW).streamItems[0].thumbnailKind).toBeDefined();
  }
});
test('스트림 항목에 원문 주소가 실린다', () => {
  expect(toTodayViewState(briefing({ streamItems: [newsItem] }), NOW).streamItems[0].originalUrl).toBe(newsItem.originalUrl);
});
test('동향 1위 문구는 동향 탭과 같다', () => {
  expect(toTodayViewState(briefing({ topTrend: ransomwareTrend }), NOW).trend?.detail).toBe(toTrendTopicView(ransomwareTrend).detail);
});
```

- [ ] **Step 2: 실패 확인** — `npx jest tests/features/today-view-state.test.ts` → FAIL
- [ ] **Step 3: 구현** — 당겨서 새로고침은 `usePullRefresh` 로 교체
- [ ] **Step 4: 통과 확인** — 공통 검증 3개 PASS
- [ ] **Step 5: 실제 화면 확인** — 로컬 서버(Task 13 Step 5 그대로) + `EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080 npx expo start` > `/pro-launch` 로 Android 에뮬레이터 캡처: 라이트·다크, 긴급 타일 누르면 인앱 브라우저로 원문
- [ ] **Step 6: 커밋** — `/pro-commit`

### Task 19: 뉴스 탭 (국내/해외 필터, 무한 목록)

**Files:**
- Create: `mobile/src/features/news/{news-screen.tsx,use-news-articles.ts}`
- Modify: `mobile/src/app/(tabs)/news.tsx` (`export default NewsScreen`)

**Interfaces:**
- Consumes: Task 15 `fetchArticlePage`, Task 16 `SbArticleRow`·`SbFilterChips`, Task 17 공통
- Produces:
  - `type NewsRegionFilter = 'all' | 'domestic' | 'overseas'`
  - `useNewsArticles(regionFilter: NewsRegionFilter)` — `useInfiniteQuery({ queryKey: ['articles', 'news', regionFilter], queryFn: ({ pageParam }) => fetchArticlePage({ kind: 'news', region: regionFilter === 'all' ? undefined : regionFilter }, pageParam), initialPageParam: undefined as string | undefined, getNextPageParam: (lastPage) => lastPage.nextCursor })`
  - `NewsScreen()` — 머리 `['뉴스']` > 칩 `전체`·`국내`·`해외` > `FlatList` + `SbArticleRow`(`toArticleRowView` 값 그대로), `onEndReached` 에서 `hasNextPage && !isFetchingNextPage` 일 때만 다음 페이지. 상태: 로딩 `SbLoadingSkeleton`, 오류 `SbErrorState`(`toAppFailure`), 빈 상태 `SbEmptyState title="아직 모은 소식이 없어요" description="새 소식이 올라오면 여기에 보여 드릴게요."`

- [ ] **Step 1: 구현**
- [ ] **Step 2: 검증** — 공통 검증 3개 PASS
- [ ] **Step 3: 실제 화면 확인** — 에뮬레이터: 칩 전환 시 목록 바뀜, 해외 항목에 한국어 제목 크게·원문 작게·`AI 번역`(키로 처리된 경우), 끝까지 스크롤하면 더 불러옴, 항목 누르면 원문, 서버를 끄고 당기면 오류 화면에 `APP_NETWORK`
- [ ] **Step 4: 커밋** — `/pro-commit`

### Task 20: 취약점 탭 (KISA 국내 공지 묶음 + KEV·고위험 CVE·GHSA)

**Files:**
- Create: `mobile/src/features/vulnerability/{vulnerability-screen.tsx,use-vulnerability-articles.ts}`
- Modify: `mobile/src/app/(tabs)/vulnerability.tsx`

**Interfaces:**
- Consumes: Task 19 와 같은 공통 + `SbCapsLabel`
- Produces:
  - `DOMESTIC_ADVISORY_PREVIEW_COUNT = 5`, `useDomesticAdvisories()` — `useQuery({ queryKey: ['articles', 'advisory', 'domestic'], queryFn: () => fetchArticlePage({ kind: 'advisory', region: 'domestic' }) })`, 앞 5건 표시
  - `useVulnerabilityArticles()` — `useInfiniteQuery` (`['articles', 'vulnerability']`, kind `vulnerability` — KEV·NVD·GHSA 모두)
  - `VulnerabilityScreen()` — 머리 `['취약점']` > `국내 공지` 라벨 + 공지 5건 > `KEV · 고위험 CVE` 라벨 + 무한 목록. 한 조회만 실패하면 그 묶음 자리에만 `SbErrorState` (다른 묶음은 그대로)

- [ ] **Step 1: 구현**
- [ ] **Step 2: 검증** — 공통 검증 3개 PASS
- [ ] **Step 3: 실제 화면 확인** — 에뮬레이터: 국내 공지 5건이 위, 아래 CVE 행에 `CVSS 9.8 (v3.1)`·`NVD · CISA KEV 등재`·`GitHub Advisory` 게시자
- [ ] **Step 4: 커밋** — `/pro-commit`

### Task 21: 동향 탭 (주간 주제 건수)

**Files:**
- Create: `mobile/src/features/trend/{trend-screen.tsx,use-weekly-trend.ts}`
- Modify: `mobile/src/app/(tabs)/trend.tsx`

**Interfaces:**
- Consumes: Task 15 `fetchWeeklyTrend`, Task 17 `toTrendTopicView`·`usePullRefresh`, `SbListRow`, `SbStatTile`
- Produces:
  - `WEEKLY_TREND_QUERY_KEY = ['weekly-trend'] as const`, `useWeeklyTrend()`
  - `TrendScreen()` — 머리 `['동향']` + 오른쪽 `toMastheadDate(weekStartDate) + ' 주'` > 1위 주제 `SbStatTile tone="feature"` > 나머지 주제 `SbListRow`(제목 주제명, detail 문구, 8종). 전체 0건이면 `SbEmptyState title="이번 주 소식을 모으고 있어요" description="소식이 쌓이면 주제별로 정리해 드릴게요."`

- [ ] **Step 1: 구현**
- [ ] **Step 2: 검증** — 공통 검증 3개 PASS
- [ ] **Step 3: 실제 화면 확인** — 에뮬레이터 캡처 (라이트·다크)
- [ ] **Step 4: 커밋** — `/pro-commit`

### Task 22: 설정 탭 (문의·약관·출처 목록·앱 버전)

**Files:**
- Create: `mobile/src/core/config/{support-links,app-info}.ts`
- Create: `mobile/src/features/settings/{settings-screen.tsx,use-collect-source-statuses.ts,settings-view-state.ts}`
- Modify: `mobile/src/app/(tabs)/settings.tsx`
- Test: `mobile/tests/features/settings-view-state.test.ts`

**Interfaces:**
- Consumes: Task 15 `fetchCollectSourceStatuses`·`toArticleDateLabel`, Task 17 `openOriginalArticle`, `SbListRow`, `SbCapsLabel`
- Produces:
  - `supportLinks = { contactEmail: '<SUPPORT_EMAIL>', supportPageUrl: 'https://cassiiopeia.github.io/secbrief/support.html', privacyPolicyUrl: 'https://cassiiopeia.github.io/secbrief/privacy.html', termsUrl: 'https://cassiiopeia.github.io/secbrief/terms.html' } as const` — `<SUPPORT_EMAIL>` 은 스펙 §11 "문의 연락처" 값 그대로 (주석: "스토어 등록정보·site/support.html 과 함께 바꿈")
  - `toContactMailUrl(appVersion: string): string` → `` `mailto:${supportLinks.contactEmail}?subject=${encodeURIComponent(`[시큐브리프] 문의 (앱 ${appVersion})`)}` ``
  - `appInfo.version` = `Constants.expoConfig?.version ?? '알 수 없음'` (expo-constants)
  - `interface SourceStatusRowView { code: string; title: string; detail: string }`, `toSourceStatusRowView(status: CollectSourceStatus, now: Date): SourceStatusRowView` — 성공 `'국내 · 공지 · 마지막 갱신 2026.11.23 09:12 · 2시간 전'`, 갱신 기록 없음 `'해외 · 취약점 · 아직 모으는 중이에요'`, 마지막 시도 실패 `'… · 마지막 갱신 … · 다음 주기에 다시 모아요'`
  - `SettingsScreen()` 구역 4개: `문의`(메일로 문의 > `Linking.openURL(toContactMailUrl(appInfo.version))`, 메일 앱이 없으면(`Linking.canOpenURL` false) 행 detail 에 주소를 보여 주고 지원 페이지 열기 / 지원 페이지 > 인앱 브라우저), `약관`(개인정보처리방침·이용약관 > 인앱 브라우저), `출처`(7개, 누름 없음), `앱 정보`(버전, 그리고 `해외 소식 제목은 AI 가 번역해요` 안내 한 줄). 출처 조회 실패 시 그 구역만 `SbErrorState`

- [ ] **Step 1: 실패 테스트 작성**

```ts
test('문의 메일 주소와 제목에 앱 버전', () => {
  expect(toContactMailUrl('0.4.0')).toBe(
    `mailto:${supportLinks.contactEmail}?subject=${encodeURIComponent('[시큐브리프] 문의 (앱 0.4.0)')}`);
});
test('문의 주소가 지원 페이지 문구와 같다', () => {
  // 단일 원본 확인: site/support.html 에 같은 주소가 있어야 함
  const supportPage = fs.readFileSync(path.join(__dirname, '../../../site/support.html'), 'utf-8');
  expect(supportPage).toContain(supportLinks.contactEmail);
});
test('출처 행: 지역·종류·마지막 갱신', () => {
  expect(toSourceStatusRowView(kisaStatus, NOW)).toEqual({ code: 'KISA', title: 'KISA 보호나라', detail: '국내 · 공지 · 마지막 갱신 2026.11.23 09:12 · 2시간 전' });
});
test('한 번도 못 모은 출처', () => {
  expect(toSourceStatusRowView({ ...ghsaStatus, lastCollectedAt: undefined }, NOW).detail).toBe('해외 · 취약점 · 아직 모으는 중이에요');
});
test('마지막 시도 실패', () => {
  expect(toSourceStatusRowView({ ...kisaStatus, isLastCollectSucceeded: false }, NOW).detail).toMatch(/다음 주기에 다시 모아요$/);
});
```

- [ ] **Step 2: 실패 확인** — `npx jest tests/features/settings-view-state.test.ts` → FAIL
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — 공통 검증 3개 PASS
- [ ] **Step 5: 실제 화면 확인** — 에뮬레이터: 문의 > 메일 앱(수신자 = 스펙 §11 주소), 개인정보처리방침·이용약관·지원 페이지가 인앱 브라우저로 열림, 출처 7개와 마지막 갱신, 버전 표시
- [ ] **Step 6: 커밋** — `/pro-commit`

### Task 23: 빌드 워크플로 운영 API 주소 + 일치 규칙 테스트

**Files:**
- Modify: `.github/workflows/PROJECT-REACT-NATIVE-EXPO-ANDROID-PLAYSTORE-CICD.yaml`, `.github/workflows/PROJECT-REACT-NATIVE-EXPO-ANDROID-TEST-APK.yaml`, `.github/workflows/PROJECT-REACT-NATIVE-EXPO-IOS-TESTFLIGHT.yaml` — 최상위 `env:` 에 `EXPO_PUBLIC_API_BASE_URL: "https://api.secbrief.suhsaechan.kr"   # 앱 번들에 박히는 운영 API 주소 (3개 워크플로 같은 값, mobile/tests/config 가 확인)`
- Test: `mobile/tests/config/build-workflow-api-url.test.ts`

**Interfaces:**
- Produces: 3개 워크플로가 같은 운영 주소로 JS 번들을 만듦. `process.env` 값이 `ENV_FILE` 로 만든 `.env` 보다 우선 (Expo 는 이미 있는 환경변수를 덮지 않음 — Step 4 로 실측)

- [ ] **Step 1: 실패 테스트 작성**

```ts
const WORKFLOW_FILE_NAMES = [
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-PLAYSTORE-CICD.yaml',
  'PROJECT-REACT-NATIVE-EXPO-ANDROID-TEST-APK.yaml',
  'PROJECT-REACT-NATIVE-EXPO-IOS-TESTFLIGHT.yaml',
];
const PRODUCTION_API_BASE_URL = 'https://api.secbrief.suhsaechan.kr';
test.each(WORKFLOW_FILE_NAMES)('%s 는 운영 API 주소로 빌드한다', (fileName) => {
  const workflowText = fs.readFileSync(path.join(__dirname, '../../../.github/workflows', fileName), 'utf-8');
  expect(workflowText).toContain(`EXPO_PUBLIC_API_BASE_URL: "${PRODUCTION_API_BASE_URL}"`);
});
```

- [ ] **Step 2: 실패 확인** — `npx jest tests/config` → FAIL
- [ ] **Step 3: 3개 워크플로에 env 추가**
- [ ] **Step 4: 통과·번들 확인** — 공통 검증 3개 PASS. `EXPO_PUBLIC_API_BASE_URL=https://api.secbrief.suhsaechan.kr npx expo export --platform android && grep -rl "api.secbrief.suhsaechan.kr" dist/` → 파일 1개 이상. 확인 후 이번에 만든 `dist/` 정리
- [ ] **Step 5: 커밋** — `/pro-commit`

---

## 배포·재제출 (사용자 승인 단계 — 자동 진행 금지)

### Task 24: 서버 배포, AI 키 등록, 첫 수집·AI 처리 확인

- [ ] **Step 1: 사전 점검** — `cd server && ./gradlew compileJava test` 전부 PASS (Docker 켠 상태), `cd mobile && npx tsc --noEmit && npx eslint . && npx jest` 전부 PASS
- [ ] **Step 2: AI 키 GitHub Secret 등록 (사용자가 값 제공)** — 사용자에게 Anthropic API 키를 받아 `/pro-github` 의 Actions Secret 기능으로 `ANTHROPIC_API_KEY` 등록 (값을 대화·로그·파일에 남기지 않음, 지어내지 않음). 키가 아직 없으면 등록을 건너뛰고 배포해도 된다 — 서버는 `[article ai] skipped : api key missing` 로그만 남기고 원문 제목으로 동작한다
- [ ] **Step 3: 사용자 승인 받고 푸시·PR** — 이슈 브랜치 푸시 > `/pro-github` 로 develop 대상 PR > 머지 (GitHub CI 대기 안 함)
- [ ] **Step 4: main 최신화** — `git fetch origin && git log origin/develop..origin/main --oneline` 에 내용이 있으면 develop 에 `git merge origin/main` (작성 시점에 `1acd697` README 버전 커밋이 main 에만 있음)
- [ ] **Step 5: 사용자 승인 받고 배포** — `/pro-changelog-deploy`. main 푸시에서 VERSION-CONTROL 이 `version_code` 8 > 9 로 올리고, `deploy` 푸시가 배포를 시작한다. **주의:** `deploy` 푸시 한 번이 서버(`server/**` 변경) + Android Play(내부 > alpha 승격) + iOS TestFlight 를 함께 돌린다. 서버 수집을 먼저 확인하고 싶으면 사용자와 상의해 레포 변수 `ANDROID_PROMOTE_TO_CLOSED_TESTING=false` 로 두고 Task 25 Step 1 에서 수동 승격한다. 앱 스토어 배포이므로 포괄 위임으로 진행하지 않는다.
- [ ] **Step 6: 배포 실측**
  - `curl -s https://api.secbrief.suhsaechan.kr/actuator/health` → `{"status":"UP"}`
  - 기동 2분 후 `curl -s https://api.secbrief.suhsaechan.kr/api/v1/sources | jq '.data[] | {code, lastStatus, lastCollectedAt}'` → 7개 모두 `SUCCESS`
  - `curl -s https://api.secbrief.suhsaechan.kr/api/v1/today | jq '.data | {todayCount, weeklyCount, stream: (.streamItems | length)}'` → `stream > 0`
  - 키를 등록했다면 10분 뒤 `curl -s 'https://api.secbrief.suhsaechan.kr/api/v1/articles?region=OVERSEAS&size=10' | jq '[.data.items[] | select(.translatedTitle != null)] | length'` → 0보다 큼
- [ ] **Step 7: 서버 로그 확인** — `/pro-ssh` 로 NAS 에서 활성 컨테이너 확인 후 `docker logs --since 15m <secbrief-back-blue|green> 2>&1 | grep -E '\[article (collect|ai)\]'` → `failed` 줄이 있으면 원인 기록, `[article ai] processed` 줄 확인. 컨테이너 환경에 키가 들어갔는지는 값 없이 `docker exec <컨테이너> sh -c 'test -n "$ANTHROPIC_API_KEY" && echo set'` 로만 확인. 특정 출처를 끄는 `update source set is_enabled = false where code = '...'` 는 **사용자 승인 후**
- [ ] **Step 8: 30분 뒤 재확인** — `/api/v1/sources` 의 RSS 4곳 `lastCollectedAt` 이 갱신됐는지 (주기 수집 동작)

### Task 25: 새 빌드 Alpha 업로드·스크린샷 교체·재제출

- [ ] **Step 1: 빌드 확인** — Actions `PROJECT-REACT-NATIVE-EXPO-ANDROID-PLAYSTORE-CICD` 결과 요약의 버전 코드가 9 이상, 내부 테스트 업로드 + alpha 승격 성공. Task 24 Step 5 에서 승격을 껐다면 Actions 에서 `workflow_dispatch` (`promote_to_closed_testing=true`)
- [ ] **Step 2: 실기기 확인** — Play 내부 테스트로 설치 > 5개 탭 실데이터, 해외 항목 한국어 제목·원문·`AI 번역`, 항목 누르면 원문, 설정의 문의·약관 (`/pro-launch`)
- [ ] **Step 3: 스토어 스크린샷 교체** — 실데이터 화면(오늘·뉴스·취약점·동향·설정) 캡처 > `docs/store/play-screenshots/` 에 저장. 샘플 캡처(`01_home.png`, `02_stream.png`) 삭제는 **사용자 승인 후** > Play Console 기본 스토어 등록정보 > 휴대전화 스크린샷 교체
- [ ] **Step 4: 뉴스 앱 선언 확인** — Play Console > 앱 콘텐츠 > 뉴스 앱: 연락처 이메일이 스펙 §11 주소, 문의 URL `https://cassiiopeia.github.io/secbrief/support.html`
- [ ] **Step 5: 사용자 승인 받고 재제출** — Play Console > 게시 개요 > 변경사항 검토 요청
- [ ] **Step 6: 완료 처리** — `/pro-report` 로 이슈에 보고서(배포 실측 포함) > 라벨 `작업완료`(`set-labels`) > 심사 통과 후 이슈 닫기

---

## Self-Review 기록

- **스펙 대응:** §1 다섯 요구 → 갱신 콘텐츠(Task 1~13, 24), 뉴스 섹션(19), 작성 시점(15·17 날짜 표기), 게시자(17 `metaLabel`), 앱 안 문의(22). §3 AI 제약 → Task 11(모델·SDK·환경변수·Secret 24). §5 출처 7곳·주기 → Task 1 시드, GHSA 7. §6 흐름 → 수집(3·9), AI 별도 주기·제목만·구조화 출력·실패 3회(10·11), 번역 표시(16·17·18), 숫자 검증(10), 추정 게시일(3). §7 테이블 → Task 1(`translated_title`, `ai_status`, `ai_attempt_count` 포함). §8 API 4개 → Task 12·13(`translatedTitle` 추가). §9 앱 → Task 14~23. §10 재제출 → Task 24·25. §11 결정 → 읽은 현황 제거(15·18), 첫 수집 범위(3), 72시간(12), CVE 병합·표시(8·17), 주제 8종·커버 재사용(1·11·15·18), 문의 연락처(22), KISA 위치(20), 서울·월요일(3·12), CVSS 버전(6·7). 반목표 → 본문 미저장(3·4·11 입력 제목만), 버전 숫자 지어내기 금지(10 숫자 검증), 샘플 금지(15 규칙 테스트), 실패해도 화면 열림(9 출처별 격리·11 AI 격리·14 에러 코드·20·22 구역별 오류).
- **스펙과 다르게(또는 더) 정한 것:** `source.feed_format` 컬럼과 BaseEntity 의 `created_at`/`updated_at` 추가. AI 처리 전 기본 주제 `GENERAL`. NVD 가 링크를 덮으면 번역을 다시 받도록 AI 상태를 PENDING 으로 되돌림. GHSA 첫 수집 7일·1페이지(100건). 지난주 대비는 "지난주 같은 경과 구간". 동향 1위에서 `general` 제외. 번역 숫자 검증은 "번역의 모든 숫자열이 원문 숫자열 집합 안"으로 보수적으로 둬서 "3개" 같은 정상 번역도 버릴 수 있음(원문 제목은 그대로 보임).
- **타입 일치:** `CollectedArticle`(3) → 수집기(4~7) → `ArticleNormalizer`(3) → `ArticleDraft` → `saveOrMerge`(8) → `ArticleCollectService`(9). `ArticleTitleAnalysisRequest`·`ArticleTitleAnalysis`(10) → `ArticleTitleAnalyzer`·`ArticleAiProcessService`(11). `ArticleTopic.getApiValue()`(1) 를 AI 스키마(11)가 씀. 앱 `BriefingItem`(15) 필드명은 서버 `ArticleResponse`(12) 와 같고 enum 만 mapper 가 소문자로 바꾼다. `toTrendTopicView`(17) 를 오늘(18)·동향(21) 이 함께 쓴다. `ArticleRowView.originalTitle`·`translationNoticeLabel`(17) 은 `SbArticleRow` props(16) 와 이름이 같다.
