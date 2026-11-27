# secbrief 서버 (Spring Boot)

공통 규칙은 루트 [CLAUDE.md](../CLAUDE.md)를 따른다. 이 문서는 서버 전용 규칙이다.

## 스택

Java 21, Spring Boot 3.5.16, Gradle 멀티모듈, PostgreSQL, Flyway. 패키지 `kr.suhsaechan.secbrief`.
의존성 저장소는 mavenCentral 만 쓴다.

## 모듈 (의존은 아래에서 위로만)

| 모듈 | 역할 |
|---|---|
| `SB-Common` | ErrorCode, CustomException, 표준 응답(ApiResponse, ErrorResponse), BaseEntity |
| `SB-Domain-Member` | 푸시 기기(토큰, 켠 알림 종류, 방해 금지 시간), 알림함 기록(긴급 알림·공지사항), 보류 푸시, 푸시 발송 규약과 Expo 구현 |
| `SB-Domain-Advisory` | 수집한 보안 공지·뉴스, 취약점, 관심 제품 |
| `SB-Application` | 여러 도메인을 묶는 유스케이스 (수집, AI 제목 분석·요약, 임베딩, 긴급 알림·알림함·공지·방해 금지, 이미지 저장) |
| `SB-Web` | 시작점, Controller, 전역 예외 처리, 스케줄러, Flyway 마이그레이션 |
| `SB-Tool` | 로컬 전용 도구 (요약 하네스 평가, 밀린 요약 적재). 서버 이미지에 들어가지 않음 |

## 규칙

- 예외는 `CustomException(ErrorCode)` 하나만 던진다. 도메인별 예외 클래스를 만들지 않는다.
- 목록 API 의 `size` 는 거부하지 않고 조정한다 (`PageSizePolicy`: 없음·1 미만 > 기본값, 최대 초과 > 최대값). 기사 목록·검색·인기 키워드·알림함 공통 (#85).
- 성공 응답은 `ApiResponse.of(data)`, 실패 응답은 전역 예외 처리가 `ErrorResponse`로 만든다.
- 스키마 변경은 Flyway 파일로만 한다 (`ddl-auto: validate`). 파일명은 `V{version.yml 버전}__설명.sql`.
- 주기 작업은 `SB-Web`의 `web.scheduler` 패키지에 둔다.
- 비밀값은 환경변수로만 받는다. `application-dev.yml`, `application-prod.yml`은 커밋하지 않는다.
- 로그는 영어로 쓰고 기능 접두사를 붙인다 (`[advisory collect] failed 502 : ...`). 외부에서 들어온 값은 정리한 뒤 넣는다.
- 클라이언트가 끊은 연결(Broken pipe, `ClientAbortException`, `AsyncRequestNotUsableException`)은 전역 예외 처리가 debug 한 줄만 남기고 응답을 쓰지 않는다 (판정은 Spring `DisconnectedClientHelper`, #58).
- 기동할 때 이 서버보다 먼저 시작해 끝나지 않은 수집 기록은 `FAILED` + `COLLECT_INTERRUPTED` 로 표시한다 (`UnfinishedCollectRunCleaner`). 새 상태값을 만들지 않는 이유: 배포 전환·되돌리기 중 이전 서버가 읽지 못함. 관리자 출처 표의 최근 실패 사유는 "중단됨"으로 보인다 (#58).

- 기사 요약은 브리핑 구조다 (한 줄 결론·무슨 일·왜 중요·영향 대상·대응, #67). 작성 규칙은 `SB-Domain-Advisory/src/main/resources/summary-harness/*.md` 가 단일 원본이다 (공통 규칙 + 글 종류별 안내 + 고쳐 쓰기).
  고칠 때는 [docs/summary-harness](../docs/summary-harness/README.md) 의 평가 순서로 이전 판과 비교한다. 요약 1건의 제공자 호출은 시도마다 최대 2회다.
  이전 구조 열(`summary`, `summary_key_points`, `summary_action_guide`)은 브리핑에서 옮긴 값을 함께 저장한다 (`ArticleBrief.toLegacySummary` 한 곳, 이전 앱 빌드용).
  상세 응답 `isSummaryPending` 은 `summary_status = PENDING` 일 때만 true 다 (입력 없음·실패·완료는 false, 앱은 true 일 때만 "준비 중" 카드, #87).
- 제목 번역은 한글로만 쓴다 (`ArticleTitleAnalysisPrompt` 지시). 원문에 없는 한자가 섞이면(`ArticleTitleAnalysisPolicy.findHanjaNotInOriginal`) 그 건만 묶어 고쳐 쓰기를 1번 부르고(`TranslatedTitleRepairPrompt`, 호출 기록 `TITLE_TRANSLATION_REPAIR`), 그래도 남으면 번역을 버려 원문 제목으로 보인다 (#87).

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
| 브리핑 없는 지난 기사 다시 요약 (#77, 기본 꺼짐: 밀린 건은 로컬 도구로) | `summary-refetch.enabled`, `summary-refetch.batch-size`, `summary-refetch.daily-article-limit`, `summary-refetch.tick-milliseconds`, `summary-refetch.initial-delay-milliseconds` | 꺼짐, 5건, 하루 20건, 1시간, 10분 ([backfill.md](../docs/summary-harness/backfill.md) 4장) |
| 출처 호출 (수집·지표·다시 받기 공통 `FeedHttpClient`) | `feed-http.prefer-ipv4` | 켜짐 (IPv4 먼저, 주소가 여러 개면 연결 실패 시 다음 주소로. 로컬 도구는 `--prefer-ipv4`) |
| 기사 이미지 처리 | `image.batch-size`, `image.tick-budget-seconds`, `image.tick-milliseconds`, `image.initial-delay-milliseconds` | 20건, 60초, 2분, 150초 |
| 제목 임베딩 | `embedding.batch-size`, `embedding.request-batch-size`, `embedding.tick-milliseconds`, `embedding.initial-delay-milliseconds` | 100건, 25건, 5분, 210초 |
| 긴급 알림 (방해 금지 보류분 발송도 같은 틱) | `push.alert-tick-milliseconds`, `push.alert-initial-delay-milliseconds`, `push.receipt-tick-milliseconds`, `push.receipt-initial-delay-milliseconds` | 5분, 2분, 15분, 3분 |
| 알림함 보관 정리 | `notification.retention-enabled`, `notification.retention-tick-milliseconds`, `notification.retention-initial-delay-milliseconds` | 켜짐, 6시간, 10분 (알림·아침 요약 기록 30일 보관, 공지사항은 지우지 않음) |
| 기사 분류 (백필·다시 분류·AI 대기 초과) | `classification.enabled`(`CLASSIFICATION_ENABLED`), `classification.tick-milliseconds`, `classification.batches-per-tick`, `classification.initial-delay-milliseconds` | 켜짐, 1분, 10번(x200건), 45초 |
| KISA 지난 글 채우기 | `collect.kisa-backfill-tick-milliseconds`, `collect.kisa-backfill-initial-delay-milliseconds` | 6시간, 3분 (`collect.enabled=false` 면 함께 꺼짐) |
| KISA 새 글 본문을 요약 입력으로 (#57) | `collect.kisa-page-text-enabled` | 켜짐 (`false` 면 KISA 글은 요약 입력 없이 저장) |
| 경보단계 (해외 지표 읽기, 지난 경보단계 글 긴급 해제) | `alert-level.enabled`, `alert-level.tick-milliseconds`, `alert-level.initial-delay-milliseconds` | 켜짐, 1시간, 20초 |

## 알림함 · 공지사항 · 방해 금지 시간 (#70)

| API (인증 없음) | 응답 `data` |
|---|---|
| `GET /api/v1/notifications?kind=ALERT\|NOTICE&cursor=&size=` | `{items, nextCursor}` (ALERT 최신순, NOTICE 고정 먼저 > 최신순, size 기본 20 · 최대 50) |
| `GET /api/v1/notifications/latest` | `{latestAlertId, latestNoticeId}` (앱 알림 점, 이용 집계에서 뺌) |
| `GET /api/v1/notifications/{id}` | 상세: 본문 전체, `linkUrl`(공지), `articleSections[{alertCategory, articles}]`(긴급 알림, 종류 순서 KEV > KISA_URGENT > CRITICAL_CVE), `articleGuides`(기사별 대응 안내 요약, #71) |
| `PUT /api/v1/push-devices` | 요청에 `quietHours {isEnabled, startTime "HH:mm", endTime, timeZone}` 선택 (없으면 저장값 유지), 응답에 `quietHours` 항상 (알림을 모두 꺼 기기를 지울 때도 `quietHours`·`digest` 는 요청 값 > 지운 행의 값 > 기본값, 관심 기술은 없음, #119) |

- 긴급 알림 틱마다 알릴 기사 전체를 알림함 기록(`notification` kind ALERT + `notification_article`) 1건으로 남기고, 그 번호를 푸시 data 에 싣는다. 발송이 전부 실패하면 기록을 지우고 기사를 대기로 남긴다.
- 푸시 data 키 원본은 `NotificationPushData` 다: `notificationId`, `notificationKind`(`ALERT`·`NOTICE`·`QUIET_HOURS_DIGEST`), `articleId`(기사 1건 알림만), `targetTab`(이전 앱용, 항상), `targetScreen`(KISA 경보단계 글 1건 알림만 `alertLevel`, 방해 금지 보류분은 보낼 때 다시 판정, #87). 앱 `toNotificationTargetRoute` 와 함께 바꾼다.
- 방해 금지 시간은 기기 시간대 기준 `[시작, 끝)`, 끝이 이르면 자정을 넘긴다. 켰을 때 시작 == 끝은 `QUIET_HOURS_SAME_TIME`, 모르는 시간대는 `INVALID_TIME_ZONE`. 기존·새 기기 모두 기본 꺼짐.
- 방해 금지 시간이면 그 기기가 받을 글을 `push_device_held_push` 에 보류하고 (KEV 도 예외 없음), 끝난 뒤 첫 알림 틱에 1건이면 그대로, 여러 건이면 모음 1건을 보낸다. 실패하면 보류분을 남긴다.
- 공지사항은 관리자 화면(`/admin/notices`)에서 쓰고, 올릴 때 "푸시도 보내기"를 고르면 알림을 켠 기기 전체에 보낸다 (방해 금지면 보류). 고칠 때는 다시 보내지 않는다. 발송 기록 화면은 `/admin/alerts`.

## 분류 태그 · 관심 기술 알림 · 아침 요약 (#93, #97)

설계: [docs/superpowers/specs/2026-11-27-interest-alerts-tag-catalog-design.md](../docs/superpowers/specs/2026-11-27-interest-alerts-tag-catalog-design.md)

- 기술 분야·생태계·제품·인기 묶음은 **DB `catalog_tag` 가 단일 원본**이다 (V0.9.1 시드, 관리자 화면 `/admin/catalog-tags` 에서 추가·이름·순서·숨김·분류 낱말). Java·앱에 목록을 두지 않는다. 기사·기기는 `code` 만 저장한다.
- 스냅샷은 `TagCatalogProvider` 한 곳 (30초마다, 관리자 변경 즉시). 분류·AI 분야 선택지·API·관심 판정이 모두 이 스냅샷을 쓴다.
- 분류(`ArticleClassifier`): 사실 값의 CPE 제조사·제품(NVD `영향 제품`) > KEV 제조사·제품 > GitHub 패키지 > 제목 속 이름 > 제목 순서로 제품 사전(가장 긴 낱말) > 분야 낱말(강한 > `~` 약한) > AI 분야 > `other`. 생태계는 GitHub 권고 ecosystem 원문 값. KISA 경보단계·보고서는 분류하지 않음.
- AI 분야는 제목 분석 호출의 스키마 `field`(카탈로그 code enum 또는 null)로 받는다 (추가 호출 없음). AI 결과 기록 때 바로 분류한다.
- 주요(`ArticleNotablePolicy`): KEV, 널리 쓰는 제품, CVSS 9.0 이상 + AI 판단 끝 + 일반 아님, 헤드라인.
- 분류 틱(`ArticleClassificationScheduler`)이 `classified_at is null` 행을 분류한다: 배포 뒤 기존 행 백필, AI 대기 60분 초과, 관리자 "기사 전체 다시 분류". 규칙으로 분야를 못 정한 예전 AI 완료 행만 AI 대기로 1번 돌린다. 로그 `[article classify] classified : targets=…, byProduct=…, byKeyword=…, byAi=…, other=…, aiRequested=…`.

| API (인증 없음) | 응답 `data` |
|---|---|
| `GET /api/v1/catalog/tags` | `{version, tags[{kind, code, name, shortName, groupCode, fieldCode, displayOrder, isWidelyUsed}]}`, `ETag` = 판, `If-None-Match` 같으면 304 |
| `GET /api/v1/articles` 추가 조건 | `sourceCodes`, `fieldCode`, `ecosystemCode`, `isNotable`, `interestProductCodes`·`interestFieldCodes`·`interestEcosystemCodes`(하나라도), `collectedSince`. 응답 기사에 `fieldCode`, `ecosystemCode`, `packageName`, `productCode`, `isNotable` |
| `GET /api/v1/articles/new-counts?timeZone=` | 기기 날짜 0시 이후 수집분 `{since, lastCollectedAt, totalCount, exploitedCount, kisaCount, newsCount, fieldCounts[], ecosystemCounts[], sourceCounts[]}` |
| `PUT /api/v1/push-devices` 추가 | `interests {productCodes, fieldCodes, ecosystemCodes}`, `digest {deliveryMode IMMEDIATE\|DIGEST, digestTime}` (없으면 저장값 유지, 카탈로그에 없는 code 는 빠짐) |
| `POST /api/v1/push-devices/alert-estimate` | 어제(기기 날짜) 수집분을 지금 규칙으로: `{date, immediatePushCount, immediateArticleCount, digestArticleCount, digestPushCount, currentPushCount}` |
| `GET /api/v1/notifications/{id}` (DIGEST) | `digest {articleCount, notableArticles, fieldSections[{fieldCode, articles}]}` |

- 응답 모양은 앱과 함께 쓰는 예시 `SB-Web/src/test/resources/contract/*.json` 과 같아야 한다 (`InterestApiContractTest`, 앱 `api-shape-contract.test.ts`).
- 알림 판정(`AlertRoutingPolicy` 한 곳): 악용 중(KEV 켬) > KISA 긴급(켬) > 관심 일치(제품은 전부, 분야·생태계는 주요만 / 긴급이면 바로, 아니면 받는 방식) > 그 밖의 고위험(`CRITICAL_CVE` 켬) > 안 알림. 뉴스는 관심 알림에 넣지 않는다.
- 알림 후보는 AI 판단이 끝나 분류된 기사만 (#97). 수집 후 60분(`Article.AI_JUDGEMENT_HOLD_DURATION`)이 지나면 규칙만으로: 고위험은 널리 쓰는 제품일 때만 알린다.
- `urgent_notified_at` 은 알림 처리 시각이다 (긴급 아님도 관심 판정을 거침, 알리지 않은 기사도 처리 표시). 실제로 보낸 기사는 알림함 기록(`notification_article`)으로 본다.
- 공통 알림 기록은 지금처럼 알림함 목록에 보인다. 관심만 맞은 여러 건 알림·아침 요약은 기기 전용 기록(`notification.push_device_id`)이라 목록에 보이지 않고 푸시로만 연다.
- 아침 요약: 대기분(`push_device_digest_item`, 48시간 보관)을 기기 시각이 요약 시각을 지난 첫 알림 틱에 1건 (방해 금지 시간이면 끝난 뒤, 하루 1번).

## 공유 링크 · 앱 링크 (#94)

앱 공유 버튼은 `https://api.secbrief.suhsaechan.kr/a/{articleId}` 를 보낸다. 경로는 `web.share.SharePaths`, 값은 `secbrief.share-link`(application.yml: 공개 주소, 앱 스킴, Apple 팀 ID·번들·App Store ID, Android 패키지·서명 SHA-256 지문)가 단일 원본이다.

| 경로 (인증 없음) | 응답 |
|---|---|
| `GET /a/{articleId}` | 미리보기 HTML (제목·출처·게시일·한 줄 결론·무슨 일·앱 열기·스토어·원문 보기, OG·Twitter 메타). 없는·숫자 아닌 id 는 같은 모양 404. 캐시 5분 (404 는 1분) |
| `GET /.well-known/apple-app-site-association` | iOS 유니버설 링크 JSON (`/a/*`), 캐시 1시간 |
| `GET /.well-known/assetlinks.json` | Android 앱 링크 JSON (Play 앱 서명 키 + 업로드 키 지문), 캐시 1시간 |
| `GET /share/app-icon.png` | 앱 아이콘 (OG 기본 그림), 캐시 7일 |

- 기사 본문은 싣지 않는다 (한 줄 결론·무슨 일만). 템플릿은 `templates/share/`, 색 값을 직접 쓰지 않는다 (`ShareTemplateRuleTest`).
- 색·아이콘은 빌드 때 레포 원본에서 복사한다 (`SB-Web/build.gradle` processResources): `brand/brand-colors.json` > `share/brand-colors.json`, `mobile/assets/images/icon.png` > `static/share/app-icon.png`. 사본을 커밋하지 않는다.
- Play 서명 키를 바꾸거나 업로드 키를 재설정하면 지문 목록도 바꾼다 (Play Console > 앱 무결성 > 앱 서명).

## 대응 모아보기 (#71)

같은 취약점을 다룬 공개 자료를 기사 상세와 알림 상세에 붙인다. 기준 값·구역 판정은 `ResponseGuidePolicy`, 조회 SQL 은 `ArticleRepository.findResponseGuideCandidates`, 조립은 `ResponseGuideQueryService` 한 곳이다.

| 응답 | 필드 |
|---|---|
| `GET /api/v1/articles/{id}` | `responseGuideSections[{section, totalCount, items[{article, sourceName, matchReason, matchedCveId, actionDueDate}]}]` (빈 구역은 뺌, 구역마다 5건). `relatedItems` 는 이전 앱 빌드용으로 계속 채움 |
| `GET /api/v1/notifications/{id}` | `articleGuides[{articleId, actions, officialCount, factCount, newsCount, leadOfficialItem}]` (알린 순서 앞 20건, NOTICE 는 빈 목록) |

- 구역은 출처 종류로만 정한다: ADVISORY 공식 대응(`OFFICIAL`), VULNERABILITY 확인된 취약점 정보(`FACT`), NEWS 관련 기사(`NEWS`). `KISA_ALERT_LEVEL` 은 넣지 않는다. 새 출처는 `source.kind` 만 맞으면 자동으로 들어간다.
- 묶는 규칙 3개 (확실한 순): 같은 CVE(전용 열·제목, 1년) > 같은 제품 이름(소문자·문장부호 정리 후 같음, 게시일 ±14일) > KISA 보안공지 제조사 이름 = 상대 이름의 앞 낱말(±14일).
- 잘못 묶이지 않게: 행사·정책·개인정보 주제는 제품으로 묶지 않음, 양쪽 모두 CVE 가 있는데 겹치지 않으면 버림, CVE 가 있는 기사에는 다른 CVE 항목을 제품 이름만으로 붙이지 않음, 제조사 앞 낱말 일치는 KISA 보안공지에만.
- 조치 기한은 KEV 사실 값(`조치 기한`) 원문 그대로, 기사 본문은 싣지 않는다 (기사는 목록과 같은 `ArticleResponse`).

## KISA 지난 글 채우기 · 사이버위기 경보 화면 (#96)

- KISA 게시판 4곳은 RSS 가 첫 쪽 10건만 줘서, 출처마다 **한 번** 게시판 목록 쪽(`/kr/bbs/list.do`)을 넘겨 채운다: 보안공지·취약점 정보·보고서는 1년, 경보단계는 게시판 전체(69건). 이후 새 글은 주기 RSS 수집이 맡는다.
  게시판 번호·메뉴 번호·범위는 `KisaBoard` 한 곳, 끝나면 `source.backfill_completed_at` 을 채워 다시 하지 않는다 (실패한 출처는 다음 틱에 다시).
  쪽 사이 2초 쉼, 최대 200쪽, 글 페이지는 읽지 않는다 (밀린 브리핑은 로컬 도구로). 게시판은 ETag·Last-Modified 를 주지 않아 일반 GET 이다. 글 주소는 RSS link 와 같은 모양으로 만들어 원문 링크 중복 확인이 그대로 맞는다.
- 주기 RSS 수집은 KISA 게시판 4곳의 새 글(그리고 요약 입력·이미지가 아직 없는 기존 글)마다 게시글 페이지를 1번 읽어 본문 칸을 요약 입력으로 쓴다 (`KisaPageSourceTextFiller` > `SourceTextRefetcher`, 요청 사이 2초, #57).
  RSS 에 요약문이 없어서다. 본문은 요약 대기 동안만 `source_text` 에 두고 요약이 끝나면 지운다 (화면에 싣지 않음). 표는 행마다 `[머리글: 칸 | ...]` 로 넣어 제품 <-> 버전 짝을 지킨다.
  못 받은 글(첨부만 있는 보고서 등)은 입력 없이 저장하고 다음 수집에서 다시 읽는다. 로그 `[kisa page text] filled : source=…, length=…`.
  보호나라 게시글에는 공공누리 표시가 없고 바닥글은 `Copyright(C) 2023 KISA. All rights reserved.` 뿐이다 (2026-11-28 확인): 본문을 옮겨 싣지 않고 직접 쓴 브리핑과 원문 링크만 보인다.
- 경보단계 글의 긴급은 게시 후 7일까지만 (`ArticleUrgencyPolicy.KISA_ALERT_LEVEL_URGENT_DURATION`): 수집할 때 지난 글은 처음부터 긴급이 아니고, 경보단계 틱이 7일 지난 글의 긴급 표시를 푼다.
- 단계는 공지 제목에서만 읽는다 (`CyberCrisisLevelTitleParser`, 5단계 밖 이름·못 읽은 제목은 단계 null + 제목 그대로). 게시판 전체 제목 69건이 시험 자료다.
- 해외 지표 2개는 공개 피드를 1시간마다 읽어 메모리에 둔다 (`InternationalAlertIndicatorStore`): CIS MS-ISAC `https://feeds.cisecurity.org/text?keys=level,color`, SANS ISC `https://isc.sans.edu/api/infocon?json`. 실패하면 이전 값을 두고, 한 번도 못 읽었으면 `level` null 로 내려 화면은 그대로 열린다.

| API (인증 없음) | 응답 `data` |
|---|---|
| `GET /api/v1/alert-level` | `{current{level, since, articleId, title, originalUrl}, changes[{articleId, fromLevel, toLevel, direction, publishedAt, title, originalUrl}], weeklyCounts{weekStart, exploitedVulnerabilityCount, urgentArticleCount, kisaNoticeCount}, internationalIndicators[{code, level, levelStep, levelCount, checkedAt, sourceUrl}], sourceUrl}` (level: `NORMAL`·`ATTENTION`·`CAUTION`·`ALERT`·`SEVERE`, direction: `RAISED`·`LOWERED`·`KEPT`, 변경 이력 최근 20건, 경보단계 글이 없으면 `current` null) |
| `GET /api/v1/articles?isUrgent=true` | 긴급 소식만 (경보단계 화면의 이번 주 긴급 건수에서 들어옴) |

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
