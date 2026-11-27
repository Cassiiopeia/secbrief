# 브리핑 로컬 적재 도구 (#49, #67, #77)

**브리핑 구조의 글이 없는 기사**의 브리핑을 이 컴퓨터에서 `claude -p` 로 만들어 운영 DB 에 싣기 위한 도구다.
대상은 두 가지다: 요약이 없는 기사(`NONE`, `PENDING`, `FAILED`)와 **이전 구조의 요약만 있는 기사**(`DONE` 이지만 `summary_one_line` 이 null).
평소 운영은 서버가 API 로 한다. 이 도구는 밀린 것과 이전 구조의 글을 한 번에 바꿀 때만 쓴다.

- 프롬프트·검증·고쳐 쓰기는 운영과 **같은 코드**다 (`ArticleSummaryHarness`, `summary-harness/*.md`). 도구에는 `claude -p` 를 부르는 부분만 있다.
- 생성 모델은 `claude-haiku-5-5` 로 고정이다 (`SummaryToolMain.GENERATION_MODEL`). 다른 모델로 브리핑을 만들지 않는다.
- 운영과 다른 점 하나: `claude -p` 는 생각(adaptive thinking)을 끌 수 없다 (운영 API 는 생각 끔). 2장 참고.
- 운영 DB 에는 `SELECT` 만 한다. 쓰기는 사람이 검토한 뒤 `apply.sql` 을 직접 적용한다.

## 1. 한 번에 실행

```bash
cd server/SB-Tool
./run-summary-backfill.sh <SSH 자격 이름> <결과 폴더(저장소 밖)> [동시 호출 수, 기본 4]
# 예: ./run-summary-backfill.sh synology-chan4760 ~/secbrief-summary-backfill/2026-11-26
# PREFER_IPV4=false ./run-summary-backfill.sh ...   # 출처 주소를 DNS 순서 그대로 시도 (기본 true: IPv4 먼저)
```

| 단계 | 하는 일 |
|---|---|
| 내보내기 | 브리핑이 없는 기사와 그 출처 값을 `SELECT` (id, 제목, 지역, 종류, 주제, 출처 코드·피드 주소, 원문 주소, CVE ID, 게시일, 상태, 요약 입력). 조건은 `to_jsonb(a) ->> 'summary_one_line' is null`: 브리핑 열이 아직 없는 DB 에서도 같은 SQL 이 동작 |
| `recover` | 요약 입력이 없는 행: 공개 출처를 **운영 수집기로** 다시 읽어 CVE ID·원문 주소로 맞춤 (NVD 는 게시일 범위, KEV 는 카탈로그 전체, GitHub 권고는 1쪽 + 1건 조회, RSS 는 지금 피드) > 못 맞춘 건은 **출처별 공개 원문 위치에서 1건씩** 다시 받음 (1-1, 요청 사이 2초, NVD 6초) |
| `generate` | 기사마다 요약 시도 최대 3회 (시도마다 작성 + 고쳐 쓰기, 호출 최대 6회) > `results.jsonl`, `report.md` |
| `apply-file` | 통과한 브리핑만 `apply.sql` 로 (브리핑 열 5개 + 이전 구조 열 3개) |

결과 폴더에 남는 것:

| 파일 | 내용 |
|---|---|
| `results.jsonl` | 기사 id, 출처 코드, 상태, 만든 브리핑(통과분, `brief` 객체), 시도·호출 수, 실패 사유. **제목·요약 입력은 없음** |
| `report.md` | 출처별 건수 (대상, 입력 있음, 통과, 탈락, 내용 없는 입력, 되찾지 못함) |
| `apply.sql` | 운영 적용 파일 |

요약 입력이 든 파일(내보낸 행, 되찾은 입력)은 임시 폴더에만 있고 스크립트가 끝나면 지워진다 (성공·실패 공통).
중간에 멈추면 같은 결과 폴더로 다시 실행한다. `results.jsonl` 에 있는 기사는 건너뛴다.

상태 값:

| 상태 | 뜻 |
|---|---|
| `ACCEPTED` | 검증을 통과한 브리핑 (적용 파일에 들어감) |
| `REJECTED` | 시도 3회 모두 검증 탈락 (그대로 둠) |
| `INPUT_TOO_THIN` | 요약할 내용이 없는 입력 (40자 미만, 주소뿐, NVD 설명이 제목에 통째로 들어 있음) |
| `NOT_RECOVERABLE` | 입력을 되찾지 못함. `failureReason` 에 사유 (`source not supported`, 응답 상태 `status=403` 등) |

### 1-1. 1건씩 다시 받는 곳 (`SourceTextRefetcher`, 서버 다시 요약과 같은 코드)

| 출처 | 받는 곳 | 꺼내는 글 |
|---|---|---|
| NVD | `services.nvd.nist.gov/rest/json/cves/2.0?cveId=` | 영어 설명 (CVSS 점수와 무관) |
| Ubuntu | 권고 주소 + `.json` | `summary` + `description` |
| Red Hat | `security.access.redhat.com/data/csaf/v2/advisories/{연도}/rhsa-{연도}_{번호}.json` | CSAF 주제(Topic) + 상세(Details), 이용 조건 문구 제외 |
| Debian | 공지 메일 보관 페이지 (`lists.debian.org`) > 실패하면 `security-tracker.debian.org/tracker/{DSA 번호}` | 메일 본문(서명 앞까지) 또는 추적 페이지의 설명·CVE·패키지별 고친 버전 표 |
| KISA 4곳 | 게시글 페이지 (`view.do`) | 본문 칸 (`.content_html`). 표는 행마다 `[머리글: 칸 \| ...]` (`SourcePageTableText`, 세로 병합 칸은 행마다 반복, #57) |
| BleepingComputer, Node.js | 기사·공지 페이지 | 본문 칸(`.articleBody`, `main`)의 문단·목록·소제목 |
| 데일리시큐 | 기사 페이지 | 게시자 요약(`og:description`)만. 저작권 고지에 "AI학습 및 활용 금지"가 있어 본문은 읽지 않음 |
| The Hacker News, KEV, GitHub 권고 | 다시 받지 않음 | THN 기사 페이지는 봇 확인(403)으로 막힘. KEV·GitHub 권고는 피드·1건 조회로 충분 |

- 받은 글은 운영과 같은 정리(`SourceTextPolicy`, 6000자 상한)를 거쳐 **요약 입력으로만** 쓴다. 임시 폴더에만 두고 끝나면 지운다 (서비스 원칙 3).
- 출처 호출은 운영과 같은 `FeedHttpClient` 다: 주소가 여러 개면 연결 실패 시 다음 주소로 넘어가고, IPv4 를 먼저 시도한다 (`--prefer-ipv4`, 운영 `secbrief.feed-http.prefer-ipv4`).

## 2. `claude -p` 호출 방식

`ClaudePrintCompletionClient` 가 기사마다 프로세스를 하나 띄운다. 옵션은 `claude --help` (v2.1.332) 로 확인한 것만 쓴다.

```
claude --print --model claude-haiku-5-5 --output-format json
       --safe-mode --tools "" --strict-mcp-config --disable-slash-commands --no-session-persistence
       --effort low --system-prompt <지시문> --json-schema <출력 스키마>      (사용자 메시지는 표준 입력)
```

| 옵션 | 이유 |
|---|---|
| `--safe-mode` | CLAUDE.md·스킬·플러그인·훅·MCP 를 모두 끔 (로그인은 그대로) |
| `--tools ""`, `--strict-mcp-config` | 내장 도구 없음, MCP 서버 없음 |
| 빈 작업 폴더 | 저장소의 CLAUDE.md·설정을 읽지 않게 저장소 밖 빈 폴더에서 실행 |
| `--no-session-persistence` | 세션을 디스크에 남기지 않음 (요약 입력이 기록에 남지 않게) |
| `--effort low` | 운영 API 와 같은 노력 값 (생각은 이 값과 무관하게 켜져 있음) |

- `--bare` 는 쓰지 않는다: API 키로만 인증해서 구독 로그인으로는 호출할 수 없다.
- **생각을 끌 수 없다.** 운영 API 는 `thinking: disabled` 인데 CLI 는 호출마다 생각 토큰을 1,100~1,600 쓴다 (실측).
  `MAX_THINKING_TOKENS=0`, `CLAUDE_CODE_DISABLE_THINKING`, `--settings '{"alwaysThinkingEnabled":false}'` 는 효과가 없었다.
  그래서 이 도구로 만든 브리핑은 운영 서버가 만드는 브리핑보다 조건이 좋다. 도구는 호출마다 답 토큰·생각 토큰을 세어 끝에 한 줄로 남긴다.
- 호출 1회는 5~10초다 (프로세스 기동 포함). 동시 4개로 분당 30건 안팎이고, 이 속도에서 사용량 제한 오류는 없었다.
- 호출이 실패하면 30초 간격으로 3번 다시 하고, 그래도 안 되면 전체를 멈춘다 (그 건은 결과에 적지 않아 다음 실행에서 다시 함).

## 3. 운영 적용 (`apply.sql`)

```sql
begin;
create temporary table brief_backfill (...) on commit drop;
copy brief_backfill (article_id, summary_one_line, summary_what_happened, summary_why_it_matters, summary_affected,
                     summary_actions, summary, summary_key_points, summary_action_guide) from stdin;
<탭으로 나눈 데이터 줄>
\.
with updated as (
    update article a set summary_one_line = b.summary_one_line, ..., summary = b.summary, ...,
           summary_status = 'DONE', source_text = null, updated_at = ...
      from brief_backfill b
     where a.id = b.article_id and a.summary_one_line is null
    returning a.id)
select (select count(*) from brief_backfill) as loaded_row_count, count(*) as updated_row_count from updated;
commit;
```

- **브리핑 열이 있는 DB(V0.8.0 배포 뒤)에만 적용한다.** 열이 없는 DB 에 넣으면 `UPDATE` 가 실패하고 전체가 롤백된다 (테스트로 확인).
- 브리핑 열 5개와 이전 구조 열 3개를 함께 쓴다. 이전 구조 값은 서버와 같은 변환(`ArticleBrief.toLegacySummary`)으로 만든다:
  `summary` = 한 줄 결론 + 무슨 일 + 왜 중요, `summary_key_points` = 영향 대상 줄, `summary_action_guide` = 대응을 이은 글.
- 만든 글은 SQL 문장이 아니라 **COPY 데이터 줄**로만 들어간다. 따옴표·`$$`·세미콜론이 SQL 로 읽히지 않는다.
  역슬래시·탭·줄바꿈은 COPY 규칙으로 이스케이프한다 (`SummaryApplyFileWriterTest` 가 실제 PostgreSQL 에 `psql` 로 넣어 확인).
- **적용 시점에 브리핑이 없는 행만** 바꾼다 (`summary_one_line is null`). 이전 요약이 `DONE` 인 행도 바뀌고, 그 사이 운영 서버가 브리핑을 쓴 행은 건드리지 않는다.
- 한 트랜잭션이고 `ON_ERROR_STOP` 이라 중간에 오류가 나면 전부 되돌린다.
- 마지막에 실은 건수(`loaded_row_count`)와 갱신한 건수(`updated_row_count`)가 나온다. 차이는 그 사이 브리핑이 생겼거나 없어진 행이다.

적용 (NAS 에서, 파일을 표준 입력으로):

```bash
docker exec -i postgres sh -c 'psql -U "$POSTGRES_USER" -d secbrief' < apply.sql
```

적용 전에 볼 것: 줄 수, 앞의 몇 줄, `report.md`.

## 4. 서버 다시 요약 (기본 꺼짐)

운영 서버도 같은 경로로 "브리핑 없는 기사"를 고칠 수 있다. **기본은 꺼짐**이다: 밀린 건은 이 도구(`claude -p`, 하이쿠)로 로컬에서 만들고, 운영 API 는 새 기사에만 쓴다.

| 설정 (`secbrief.summary-refetch.`) | 기본값 | 뜻 |
|---|---|---|
| `enabled` | `false` | `true` 면 스케줄러 빈을 만듦 (`SourceTextRefetchSummaryScheduler`) |
| `batch-size` | 5 | 한 틱 대상 기사 수 |
| `daily-article-limit` | 20 | 하루(서울 날짜) 대상 기사 상한 (다시 받기 시도 기준, `summary.daily-limit` 와 별도) |
| `tick-milliseconds`, `initial-delay-milliseconds` | 1시간, 10분 | 틱 주기, 첫 틱 대기 |

- 대상: `summary_one_line` 과 `source_text` 가 없고 상태가 `DONE`(이전 구조)·`NONE`(입력 없음)인 기사, 1-1 의 다시 받는 출처만. `FAILED` 는 다시 부르지 않는다.
- 흐름: 제공자 확인 > 하루 예산(`AI_DAILY_BUDGET_USD`) > 하루 상한 > 최신 id 부터 `batch-size` 건 > 1건씩 다시 받기 > 요약 시도 1회 (작성 + 고쳐 쓰기, 호출 최대 2회) > 브리핑 반영.
- 반영은 그 사이 브리핑이 생겼거나 새 입력으로 대기(`PENDING`) 중이면 하지 않는다. 실패한 건은 그대로 둔다 (상태를 바꾸지 않음, 이전 구조 요약은 계속 보임).
- 한 번 끝까지 훑으면 다음 서울 날짜에 다시 처음부터 (서버를 다시 띄워도 처음부터). 로그 `[summary refetch] processed : done=…, rejected=…, notRecovered=…`.

## 5. 하지 않는 것

- 다시 받은 글을 저장하거나 화면에 싣지 않는다. 요약 입력으로만 쓰고 지운다 (서비스 원칙 3).
- 봇 확인으로 막힌 페이지(The Hacker News)를 우회하지 않는다. User-Agent 를 바꾸지 않는다.
- 데일리시큐 기사 본문을 읽지 않는다 (게시자 요약만).
- 공개 출처가 더는 주지 않는 기사는 그대로 둔다 (원문 링크로 열림, 서비스 원칙 5).
- `claude -p` 를 운영 제공자 목록에 넣지 않는다.

## 6. 실행 기록

### 원문 재수집 고친 뒤 다시 만들기 (#77, 2026-11-27)

대상 711건 (브리핑 없는 기사 전체: #96 KISA 지난 글 채우기로 KISA 가 47 > 443건), 동시 호출 4개, 되찾기 25분 + 만들기 30분. 운영 DB 에는 적용하지 않은 상태다 (적용 파일만 만듦, 666건).

| 출처 | 대상 | 입력 있음 | 통과 | 탈락 | 내용 없는 입력 | 되찾지 못함 | 되찾은 곳 |
|---|---:|---:|---:|---:|---:|---:|---|
| KISA 보안공지 | 344 | 344 | 344 | 0 | 0 | 0 | 게시글 본문 칸 |
| KISA 경보단계 | 69 | 69 | 69 | 0 | 0 | 0 | 게시글 본문 칸 |
| KISA 보고서 | 20 | 17 | 17 | 0 | 0 | 3 | 게시글 본문 칸 (3건은 본문 칸 없음: 첨부만) |
| KISA 취약점 정보 | 10 | 10 | 10 | 0 | 0 | 0 | 게시글 본문 칸 |
| 데일리시큐 | 97 | 95 | 95 | 0 | 0 | 2 | 게시자 요약 (2건은 기사 삭제 404) |
| Red Hat | 61 | 61 | 61 | 0 | 0 | 0 | CSAF |
| BleepingComputer | 31 | 31 | 31 | 0 | 0 | 0 | 기사 본문 문단 |
| Debian | 30 | 30 | 30 | 0 | 0 | 0 | 보안 추적 페이지 (메일 보관 서버 503) |
| The Hacker News | 29 | 0 | 0 | 0 | 0 | 29 | 다시 받지 않음 (봇 확인 403) |
| NVD | 12 | 12 | 1 | 0 | 11 | 0 | CVE 1건 조회 (11건은 설명이 제목에 통째로 들어 있음) |
| Ubuntu | 7 | 7 | 7 | 0 | 0 | 0 | 권고 JSON |
| Node.js | 1 | 1 | 1 | 0 | 0 | 0 | 공지 본문 |
| 합계 | 711 | 677 | **666** | 0 | 11 | 34 | |

- 제공자 호출 752회 (기사당 1.13회), 고쳐 쓴 글로 통과 79건.
- 원인 (이전 실행의 되찾지 못함 315건):
  - Ubuntu: IPv6 가 아니었다. `ubuntu.com` A 레코드 3개 중 `185.125.190.20` 이 연결을 받지 않는 때가 있고, 이전 HTTP 클라이언트(`HttpURLConnection`)는 첫 주소만 시도해 5초 연결 제한에 걸렸다 (curl 은 다음 주소로 넘어감). `-Djava.net.preferIPv4Stack=true` 로 7건을 되찾은 것은 DNS 순서가 바뀐 우연이다. 운영 수집에서도 3일 동안 Ubuntu·Debian·NVD 가 1번씩 같은 오류(`SocketTimeoutException`)를 냈다. `FeedHttpClient` 를 주소마다 시도하는 클라이언트(Apache HttpClient 5)로 바꿨다.
  - Debian: 피드 요약문 자리에 추적 페이지 주소만 있어 요약 입력이 생기지 않았다 (요약할 내용 없음). 공지 메일 보관 페이지는 이날 503 이라 보안 추적 페이지로 받았다.
  - Red Hat·Ubuntu·뉴스: 피드가 최근 글만 준다 (Red Hat 3일, Ubuntu 10건, 뉴스 15~50건).
  - KISA·Node.js: 피드에 요약문이 없다.
  - NVD: 설명이 제목에 통째로 들어 있는 CVE (규칙대로 요약 입력이 아님). 이번부터 `INPUT_TOO_THIN` 으로 센다.

### 브리핑 구조로 다시 만들기 (#67, 2026-11-26)

대상 1186건 (브리핑이 없는 기사 전체: 요약 없음 + 이전 구조 요약만 있음), 동시 호출 4개, 약 35분. 운영 DB 에는 적용하지 않은 상태다 (적용 파일만 만듦, 871건).

| 출처 | 대상 | 입력 있음 | 통과 | 탈락 | 내용 없는 입력 | 되찾지 못함 |
|---|---:|---:|---:|---:|---:|---:|
| NVD | 497 | 485 | 485 | 0 | 0 | 12 |
| Red Hat | 206 | 145 | 145 | 0 | 0 | 61 |
| 데일리시큐 | 147 | 50 | 50 | 0 | 0 | 97 |
| The Hacker News | 79 | 50 | 50 | 0 | 0 | 29 |
| GitHub 권고 (critical, high) | 78 | 78 | 78 | 0 | 0 | 0 |
| BleepingComputer | 46 | 15 | 15 | 0 | 0 | 31 |
| CISA KEV | 41 | 41 | 41 | 0 | 0 | 0 |
| Debian | 30 | 0 | 0 | 0 | 0 | 30 |
| Ubuntu | 14 | 7 | 7 | 0 | 0 | 7 |
| KISA 4곳 | 47 | 0 | 0 | 0 | 0 | 47 |
| Node.js | 1 | 0 | 0 | 0 | 0 | 1 |
| 합계 | 1186 | 871 | **871** | 0 | 0 | 315 |

- 제공자 호출 980회 (기사당 1.13회), 고쳐 쓴 글로 통과 93건. 한 줄 결론 평균 44.9자, 최대 60자.
- Ubuntu 피드는 JVM 기본(IPv6 우선)으로는 읽기 시간 초과가 났다 (curl 은 1초). `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true` 로 다시 실행해 7건을 되찾음. 나머지 7건은 피드에서 밀려남.
- Red Hat 61건, 뉴스 157건은 피드에서 이미 밀려남. Debian 30건은 이번에는 되찾기 단계에서 입력이 생기지 않음 (#49 에서는 주소뿐인 입력 20건, 원인 미확인).
- 적용 파일은 V0.8.0 배포 뒤에만 적용한다 (3장).

### 밀린 요약 채우기 (#49, 2026-11-26, 이전 구조)

대상 799건 (요약이 없는 기사 전체), 동시 호출 4개, 20분. 운영 DB 에는 적용하지 않은 상태다 (적용 파일만 만듦).

| 출처 | 대상 | 입력 있음 | 통과 | 탈락 | 내용 없는 입력 | 되찾지 못함 |
|---|---:|---:|---:|---:|---:|---:|
| NVD | 449 | 437 | 437 | 0 | 0 | 12 |
| Red Hat | 89 | 89 | 89 | 0 | 0 | 0 |
| 데일리시큐 | 79 | 13 | 13 | 0 | 0 | 66 |
| CISA KEV | 34 | 34 | 34 | 0 | 0 | 0 |
| The Hacker News | 32 | 9 | 9 | 0 | 0 | 23 |
| BleepingComputer | 31 | 2 | 2 | 0 | 0 | 29 |
| Debian | 20 | 20 | 0 | 0 | 20 | 0 |
| KISA 4곳 | 47 | 0 | 0 | 0 | 0 | 47 |
| Ubuntu | 9 | 9 | 9 | 0 | 0 | 0 |
| GitHub 권고 (critical, high) | 8 | 8 | 8 | 0 | 0 | 0 |
| Node.js | 1 | 0 | 0 | 0 | 0 | 1 |
| 합계 | 799 | 621 | **601** | 0 | 20 | 178 |

- 제공자 호출 618회 (기사당 1.03회). 첫 초안으로 통과 588건, 고쳐 쓴 글로 통과 13건 (그중 1건은 시도 3회).
- 되찾지 못한 178건: 뉴스 118건은 피드에서 이미 밀려남, KISA 47건은 피드에 요약문이 없음,
  NVD 12건 중 11건은 설명이 짧아 제목에 통째로 들어 있음(규칙대로 요약 입력이 아님), NVD 1건과 Node.js 1건은 다시 받은 응답에서 찾지 못함(원인 미확인).
- 만든 적용 파일을 로컬 PostgreSQL 16 에 넣어 확인: 601건을 싣고, 미리 `DONE` 으로 둔 5건과 없는 행 3건을 뺀 593건만 갱신, 들어간 글은 결과 파일과 글자까지 같음.
