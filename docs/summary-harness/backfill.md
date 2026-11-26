# 브리핑 로컬 적재 도구 (#49, #67)

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
```

| 단계 | 하는 일 |
|---|---|
| 내보내기 | 브리핑이 없는 기사와 그 출처 값을 `SELECT` (id, 제목, 지역, 종류, 주제, 출처 코드·피드 주소, 원문 주소, CVE ID, 게시일, 상태, 요약 입력). 조건은 `to_jsonb(a) ->> 'summary_one_line' is null`: 브리핑 열이 아직 없는 DB 에서도 같은 SQL 이 동작 |
| `recover` | 요약 입력이 없는 행: 공개 출처를 **운영 수집기로** 다시 읽어 CVE ID·원문 주소로 맞춤 (NVD 는 게시일 범위, KEV 는 카탈로그 전체, GitHub 권고는 1쪽 + 1건 조회, RSS 는 지금 피드) |
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
| `INPUT_TOO_THIN` | 요약할 내용이 없는 입력 (주소뿐: Debian 권고) |
| `NOT_RECOVERABLE` | 공개 출처가 더는 주지 않아 입력을 되찾지 못함 (피드에서 밀려난 기사, 요약문이 없는 KISA 피드) |

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

## 4. 하지 않는 것

- 기사 본문 페이지를 읽지 않는다. 피드와 공개 API 가 준 글만 쓴다 (서비스 원칙 3).
- KISA 공지 페이지의 글을 읽지 않는다. KISA 피드는 요약문이 없어 요약 입력이 생기지 않는다 (결정이 필요한 항목).
- 공개 출처가 더는 주지 않는 기사는 그대로 둔다.
- `claude -p` 를 운영 제공자 목록에 넣지 않는다.

## 5. 실행 기록

### 브리핑 구조로 다시 만들기 (#67, 2026-11-26)

{{BACKFILL_67}}

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
