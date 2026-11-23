# 실제 콘텐츠 구축 (Play 재제출) 설계

- 날짜: 2026-11-23
- 상태: 초안 (승인 전)
- 상위 설계: `2026-11-20-secbrief-foundation-design.md`

## 1. 왜 하는가

Google Play가 비공개 테스트(Alpha, 버전 코드 8)를 **뉴스 및 잡지 정책 위반**으로 거부했다(2026-11-23 메일).
지적 내용과 현재 상태가 모두 일치한다.

| 구글 요구 | 현재 상태 |
|---|---|
| 정기적으로 갱신되는 콘텐츠 (정적 금지, 3개월 이내) | 홈은 코드에 고정한 샘플 5건 (`sample-today-briefing-source.ts`) |
| 뉴스 섹션이 작동 | 뉴스·동향·취약점·설정 탭이 '준비 중' 빈 화면 |
| 콘텐츠 작성 시점 확인 가능 | 날짜 고정값 |
| 기사마다 원본 출처(게시자) 표시 | 출처 링크·게시자 없음 |
| 앱 안 문의 페이지 (이메일·전화) | 없음 (웹 `support.html`에만 있음) |

목표는 **이 다섯 가지를 실제 기능으로 채워 다시 제출**하는 것이다. 이 기능들은 어차피 서비스의 핵심이라 임시방편이 아니다.

## 2. 목표 · 비목표 · 반목표

**목표**
- 서버가 공개 출처를 주기적으로 수집해 저장한다.
- 앱의 오늘·뉴스·취약점·동향·설정 탭이 실제 데이터로 작동한다.
- 모든 항목에 게시자, 게시일, 원문 링크가 보인다.
- 설정 탭에 문의(이메일), 개인정보처리방침, 이용약관, 출처 목록이 있다.
- 새 빌드로 Play 비공개 테스트를 다시 제출해 통과한다.

**비목표 (이번 범위 밖)**
- AI 본문 요약·영향 버전 정리 (14일 테스트 기간에 붙인다). 이번에는 AI를 **제목 분류와 해외 제목 한국어 번역**에만 쓴다
- 푸시 알림 (Firebase 콘솔 설정은 끝났지만 APNs 키 미확보. 별도 설계)
- 회원·내 스택 맞춤 (`isMyStackMatch`), 조치 상태 기록

**반목표 (이렇게 되면 기술적으로 돌아가도 실패)**
- 기사 본문을 옮겨 싣는다 (제목·링크·게시자·날짜만).
- 원문에 없는 버전 숫자를 만들어 보여 준다.
- 운영 빌드에 샘플 데이터가 섞여 나간다.
- 수집이 실패했을 때 앱 화면이 열리지 않는다.

## 3. 제약

- 서버: Java 21, Spring Boot 3.5 멀티모듈, PostgreSQL(NAS 공용), Flyway(`ddl-auto: validate`), Synology Blue-Green 배포(`api.secbrief.suhsaechan.kr`, `deploy` 브랜치 푸시).
- 앱: Expo SDK 58, Expo Router, TanStack Query. 앱은 우리 서버하고만 통신한다.
- 공개 출처만 수집한다. 회사 내부 자료는 어디에도 넣지 않는다.
- 각 출처의 이용 조건(요청 빈도, 표시 방식)을 지킨다. NVD API는 키 없이 30초당 5회 제한, GitHub API는 인증 없이 시간당 60회.
- AI: Claude Haiku 5.5(`claude-haiku-5-5`, 입력 $0.10/출력 $0.50 per 1M). 서버 환경변수 `ANTHROPIC_API_KEY`(GitHub Secret 추가). 공식 Anthropic Java SDK 사용.

## 4. 접근 방식

| 방식 | 장점 | 단점 | 판단 |
|---|---|---|---|
| **A. Spring 서버 안 스케줄러로 수집** | 기존 `SchedulingConfig`·`web.scheduler`·배포를 그대로 사용. 배포 1개 | Java RSS 파싱 라이브러리 추가 필요 | **채택** |
| B. Python 수집기를 따로 두고 같은 DB에 쓰기 | 파싱이 간단 | 배포·언어 1개씩 증가, 스키마 이중 관리 | 제외 |
| C. 외부 RSS 모음 서비스 사용 | 가장 빠름 | 외부 의존, "우리 서버만 통신"·"공개 출처만" 원칙과 충돌 | 제외 |

## 5. 출처 (1차, 수집 경로 2026-11-23 확인)

| 출처 | 분류 | 지역 | 경로 | 주기 |
|---|---|---|---|---|
| KISA 보호나라 보안공지 | 공지 | 국내 | RSS `https://www.boho.or.kr/kr/rss.do?bbsId=B0000133` | 30분 |
| CISA KEV | 취약점 | 해외 | JSON `https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json` | 6시간 |
| NVD | 취약점 | 해외 | API 2.0, CVSS 9.0 이상만 | 2시간 |
| 데일리시큐 | 뉴스 | 국내 | RSS `https://www.dailysecu.com/rss/allArticle.xml` | 30분 |
| The Hacker News | 뉴스 | 해외 | RSS `https://feeds.feedburner.com/TheHackersNews` | 30분 |
| BleepingComputer | 뉴스 | 해외 | RSS `https://www.bleepingcomputer.com/feed/` | 30분 |
| GitHub 보안 권고(GHSA) | 취약점 | 해외 | API `https://api.github.com/advisories?type=reviewed&severity=critical` (npm·maven·pip 등 제품별, 예: Next.js) | 2시간 |

출처 범위(사용자 결정 2026-11-23): **공인 출처와 보안 매체를 둘 다** 본다. 공인 출처가 근거, 매체가 맥락.
보류: 보안뉴스(RSS 주소 이동), 개인정보위 보도자료(주소 변경). 제품 공식 공지는 GHSA로 대신하고 개별 제품 사이트는 2차.
출처는 코드가 아니라 `source` 테이블에 둔다. 켜고 끄기는 `is_enabled`.

## 6. 처리 흐름

```
스케줄러(출처별 주기) > 수집기(RSS/JSON/API) > 정규화 > 중복 제거(원문 URL·CVE ID) > 긴급 판정 > 저장 > AI 처리(분류·번역, 별도 주기)
                                        └ 실패: 수집 기록에 남기고 다음 주기 재시도 (기존 데이터는 그대로 제공)
```

- **정규화:** 제목, 원문 URL, 게시자, 게시일, 지역, 종류(뉴스/공지/취약점). 본문은 저장하지 않는다.
- **AI 처리 (Haiku 5.5, 사용자 결정 2026-11-23):** 저장된 미처리 항목을 5분마다 묶어서 처리. 제목만 입력(본문 없음). 출력은 구조화 출력(JSON)으로 주제 1개와, 해외 항목이면 한국어 제목. 실패하면 주제 `general`·번역 없음으로 두고 다음 주기에 재시도(최대 3회). 화면은 AI 처리 전에도 원문 제목으로 바로 보인다.
- **번역 표시:** 한국어 제목을 크게, 원문 제목을 작게 함께 보여 주고 'AI 번역' 표시. 버전 숫자·CVE ID·제품명은 원문 그대로 둔다(프롬프트로 지시하고, 결과에 원문에 없는 숫자가 생기면 번역을 버린다).
- **긴급 판정 (상위 설계 기준):** CVSS 9.0 이상, KEV 등재, KISA 공지 제목에 '긴급'.
- **게시일:** 출처가 준 값을 쓰고, 없으면 수집 시각을 쓰되 `is_published_at_estimated`로 표시한다.

## 7. 데이터 구조 (Flyway, 버전은 version.yml 따라)

- `source`: id, code, display_name, kind, region, feed_url, interval_minutes, is_enabled, last_collected_at, last_status
- `article`: id, source_id, kind(NEWS/ADVISORY/VULNERABILITY), title, translated_title(null 허용), original_url(unique), publisher_name, published_at, is_published_at_estimated, region, topic, ai_status(PENDING/DONE/FAILED), ai_attempt_count, cve_id(unique, null 허용), cvss_score, cvss_version, is_kev, is_urgent, collected_at
- `collect_run`: id, source_id, started_at, finished_at, status, new_count, error_code, error_message(정리된 값)

상위 설계의 `advisory_*`, `member*`, `push_*` 테이블은 이번에 만들지 않는다.

## 8. API (`/api/v1`, 응답은 `ApiResponse`)

| 메서드·경로 | 용도 |
|---|---|
| `GET /today` | 오늘 탭: 긴급 항목, 최신 스트림, 오늘 건수, 이번 주 1위 주제 |
| `GET /articles?kind=&region=&topic=&cursor=&size=` | 뉴스·취약점 목록 (커서 페이징, 최신순) |
| `GET /trends/weekly` | 동향: 주제별 이번 주 건수와 지난주 대비 증감 |
| `GET /sources` | 설정: 출처 목록, 마지막 갱신 시각 |

인증 없음(읽기 전용). 요청 빈도 제한은 Traefik 기본값을 따른다.

## 9. 앱

- **데이터:** `sample-today-briefing-source`를 API 클라이언트로 교체한다. 운영 빌드에서 샘플 소스는 import되지 않게 아키텍처 테스트로 막는다.
- **모든 목록 항목:** 제목, 게시자, 게시일(절대 날짜 + 상대 시간), 지역·종류 표시. 누르면 원문을 인앱 브라우저로 연다.
- **탭:** 오늘(실데이터), 뉴스(국내/해외 필터), 취약점(KEV·고위험 CVE), 동향(주간 주제 건수), 설정.
- **설정:** 문의(메일 앱 열기 + 지원 페이지), 개인정보처리방침, 이용약관, 출처 목록과 마지막 갱신 시각, 앱 버전.
- **실패 상태:** 서버 응답 실패 시 오류 화면에 에러 코드를 함께 보여 준다(문구 규칙).

## 10. 재제출

1. 서버를 `deploy`로 배포하고 출처별 첫 수집이 쌓였는지 확인한다.
2. 새 버전(버전 코드 9 이상)을 빌드해 Alpha 트랙에 올린다.
3. 스토어 스크린샷을 실데이터 화면으로 교체한다(샘플 화면 삭제).
4. 뉴스 앱 선언의 연락처 URL을 확인하고, 게시 개요에서 다시 제출한다.

## 11. 세부 결정 (설계자 판단, 검토자 질문 반영)

| 항목 | 결정 | 이유 |
|---|---|---|
| 오늘 탭의 읽은 현황(2/5)·연속 확인 일수 | 이번 빌드에서 **뺀다**. `TodayBriefing`에서 `readCount`·`streakDays` 제거, 해당 타일 자리에 '이번 주 건수' 표시 | 회원 기능이 없어 실제 값을 만들 수 없음. 지어낸 값은 반목표 |
| 첫 수집 범위 | RSS: 피드가 주는 만큼. KEV: `dateAdded` 최근 30일. NVD: 게시일 최근 7일 중 CVSS 9.0 이상 | 화면이 비지 않으면서 옛 항목이 오늘·동향을 덮지 않게 |
| 오늘 탭 긴급 항목 유지 기간 | 게시 후 72시간 | 주말을 넘겨도 비지 않게 |
| NVD·KEV 같은 CVE | `cve_id`로 **한 건으로 합친다**. 원문 링크·게시자는 NVD를 유지하고 KEV 등재 시 `is_kev=true`로 갱신. 표시: "NVD · CISA KEV 등재" | 같은 CVE가 두 번 보이면 안 됨. 스키마를 단순하게 유지 |
| 주제 분류 | 8종: `vulnerability`, `ransomware`, `privacy`, `remoteAccess`, `database`, `supplyChain`, `policy`, `general`. 새 3종의 커버는 기존 그림 재사용. 분류는 Haiku(§6) | 보안 유형별로 나누되 새 그림 제작은 범위 밖 |
| 문의 연락처 | 이메일 `chan4760@gmail.com` (스토어·지원 페이지와 같은 값). 이용약관은 기존 `site/terms.html` 사용 | 구글 요구는 이메일 또는 전화 |
| KISA 공지 위치 | 취약점 탭 상단 '국내 공지' 묶음 + 오늘 탭 스트림 | 국내 취약점 정보가 KISA 공지뿐 |
| 오늘·이번 주 기준 | 한국 시간(Asia/Seoul), `published_at` 기준. 주는 월요일 시작 | 서버 `time_zone` 설정과 일치 |
| CVSS 점수 | NVD 기본 점수: v3.1 우선, 없으면 v4.0. 점수 버전을 `cvss_version`에 함께 저장 | 원문 값 그대로 싣기(원칙 2) |

## 12. 빌더에게 맡기는 것

RSS 파싱 라이브러리 선택, 패키지·클래스 이름, 페이징 기본 크기, 키워드 규칙 목록의 세부, 화면 세부 배치(기존 디자인 시스템 안에서).
