# 요약 하네스 평가 (#49)

같은 표본에 **기준선(하네스 이전 지시문, 호출 1회)** 과 **하네스(글 종류별 규칙 + 고쳐 쓰기)** 를 돌려 비교한다.
생성은 둘 다 `claude-haiku-5-5` 만 쓴다. 채점만 더 강한 모델을 쓴다.

## 1. 평가 기준

### 자동 (운영과 같은 `ArticleSummaryPolicy`)

| 지표 | 뜻 |
|---|---|
| 첫 초안 통과 | 작성 1회의 요약이 검증을 통과한 비율 |
| 첫 초안이 버린 곳 없이 통과 | 핵심 정리·대응 안내도 하나도 버리지 않고 통과 |
| 최종 통과 | 고쳐 쓰기까지 한 뒤 통과 (시도 1회 기준, 운영은 시도 3회라 더 높아짐) |
| 표본당 제공자 호출 | 비용 |

첫 초안 통과는 두 가지 검증 규칙으로 센다: **이전 규칙**(v0.6.1 운영)과 **현재 규칙**(이번에 고친 규칙).
규칙을 느슨하게 해서 숫자가 좋아진 것과 글이 좋아진 것을 구분하기 위해서다.

### 채점 (LLM 심사, 기준 6개, 1~5점)

채점 지시문: `server/SB-Tool/src/main/resources/summary-evaluation/judge-instruction.md`. 어느 쪽 글인지 알려 주지 않는다.

| 기준 | 5점 | 1점 |
|---|---|---|
| faithfulness | 모든 사실·숫자·버전·날짜·CVE ID·이름이 입력(제목 포함)에 있음 | 지어낸 숫자·버전·날짜·사건 |
| coverage | 무슨 일·누가 영향받나·왜 중요한가를 입력에 있는 만큼 담음 | 요지가 빠지거나 틀림 |
| actionGuide | 입력에 적힌 조치만, 없으면 null | 지어낸 조언 |
| koreanStyle | 해요체·능동형, 번역투 없음, 글 자체를 가리키지 않음 | 어색하거나 틀린 한국어 |
| originality | 고르고 다시 엮어 자기 말로 씀 | 문장을 그대로 옮기거나 직역 |
| concision | 군더더기·반복 없음, 핵심 정리가 요약을 되풀이하지 않음 | 장황함 |

## 2. 실행 방법

요약 입력이 든 파일은 저장소 밖 임시 폴더에 두고 끝나면 지운다. 아래에서 `W` 는 그 임시 폴더다.

```bash
cd server
TOOL="./gradlew -q :SB-Tool:summaryTool"

# 1) 표본 만들기: 입력이 든 대상 파일(inputs.jsonl, backfill.md 의 recover 결과)에서 출처·글 종류별로 고르게
$TOOL --args="sample --inputs $W/inputs.jsonl --per-group 6 --output $W/sample.jsonl"

# 2) 생성 (둘 다 claude-haiku-5-5): 기준선은 이전 지시문 + 호출 1회, 하네스는 현재 규칙 + 고쳐 쓰기
$TOOL --args="evaluate --variant baseline --sample $W/sample.jsonl --output $W/eval-baseline.jsonl --empty-directory $W/empty"
$TOOL --args="evaluate --variant harness  --sample $W/sample.jsonl --output $W/eval-harness.jsonl  --empty-directory $W/empty"

# 3) 채점 (생성보다 강한 모델로만)
$TOOL --args="judge --judge-model claude-sonnet-5-5 --sample $W/sample.jsonl --outputs $W/eval-baseline.jsonl --scores $W/scores-baseline.jsonl --empty-directory $W/empty"
$TOOL --args="judge --judge-model claude-sonnet-5-5 --sample $W/sample.jsonl --outputs $W/eval-harness.jsonl  --scores $W/scores-harness.jsonl  --empty-directory $W/empty"

# 4) 보고서 (숫자만 담김)
$TOOL --args="evaluation-report --variant baseline --sample $W/sample.jsonl --outputs $W/eval-baseline.jsonl --scores $W/scores-baseline.jsonl --output $W/report-baseline.md"
$TOOL --args="evaluation-report --variant harness  --sample $W/sample.jsonl --outputs $W/eval-harness.jsonl  --scores $W/scores-harness.jsonl  --output $W/report-harness.md"
```

- 기준선 지시문은 비교용으로 굳혀 둔 사본이다 (`summary-evaluation/baseline-system-instruction.txt`, v0.6.1 의 `SYSTEM_INSTRUCTION`). 운영에는 쓰지 않는다.
- 보고서의 "첫 초안 통과"는 저장된 초안을 그 시점의 검증 규칙으로 다시 계산한다. 검증 규칙만 바꿨다면 생성을 다시 돌리지 않아도 된다.
- 출력 파일에 이미 있는 표본은 건너뛴다. 다시 재려면 출력 파일 이름을 바꾼다.

## 3. 측정 결과 (2026-11-26)

- 표본 56건: 운영 DB 의 요약 대기·미작성 기사 중 공개 출처에서 입력을 다시 받은 것 + 최근 3일 뉴스 (모두 공개 피드·공개 API 가 준 글).
  NVD 6, CISA KEV 6, GitHub 권고 8, Red Hat 6, Ubuntu 6, 데일리시큐 10, The Hacker News 8, BleepingComputer 6.
  글 종류로는 `CVE_ENTRY` 12, `PACKAGE_ADVISORY` 8, `VENDOR_ADVISORY` 12, `NEWS_ARTICLE` 18, `EVENT_NOTICE` 6.
- 생성: `claude-haiku-5-5` (`claude -p --effort low`). 채점: `claude-sonnet-5-5`, 1회.

### 자동 지표

| 지표 | 기준선 | 하네스 |
|---|---:|---:|
| 첫 초안 통과 (이전 검증 규칙) | 41 / 56 (73%) | 42 / 56 (75%) |
| 첫 초안 통과 (현재 검증 규칙) | 51 / 56 (91%) | 54 / 56 (96%) |
| 최종 통과 (고쳐 쓰기 포함) | 51 / 56 (91%) | **56 / 56 (100%)** |
| 표본당 제공자 호출 | 1.00 | 1.04 |
| 요약 평균 길이 | 276자 | 166자 |
| 핵심 정리 평균 개수 | 3.31 | 2.00 |

- 기준선이 이전 규칙에서 탈락한 15건: Red Hat 6건 전부(긴 제품 이름을 정확히 적어서), NVD 1건(조치 버전 나열을 정확히 적어서),
  데일리시큐 5건(한국어 문장을 그대로 옮김), 숫자 3건(영어 수 낱말·달 이름을 숫자로 옮김: six > 6, eight > 8 같은 것).
  운영 DB 의 `FAILED` 분포(Red Hat 23, 데일리시큐 14)와 같은 모양이다.
- **통과율이 오른 것의 대부분은 검증 규칙을 고친 효과다** (73% > 91%). 지시문만으로는 이전 규칙에서 73% > 75% 로 거의 같다.
  남은 탈락(한국어 옮겨 싣기 5건)은 지시문이 줄였고(5 > 2), 나머지는 고쳐 쓰기가 모두 살렸다.

### 채점 평균 (1~5점)

| 기준 | 기준선 (통과한 51건) | 하네스 (56건) | 차이 |
|---|---:|---:|---:|
| faithfulness | 3.92 | **4.30** | +0.38 |
| coverage | **4.43** | 4.16 | −0.27 |
| actionGuide | 3.98 | **4.41** | +0.43 |
| koreanStyle | 3.94 | **4.21** | +0.27 |
| originality | 3.14 | **3.43** | +0.29 |
| concision | 2.65 | **3.45** | +0.80 |

faithfulness 5점: 기준선 4건, 하네스 19건. 2점 이하는 양쪽 모두 0건.

### 글 종류별 (하네스 − 기준선)

| 글 종류 | 표본 | faithfulness | coverage | actionGuide | koreanStyle | originality | concision |
|---|---:|---:|---:|---:|---:|---:|---:|
| CVE_ENTRY | 12 | 4.08 > 4.17 | 4.25 > 4.17 | 3.75 > 4.00 | 3.67 > 4.08 | 2.92 > 3.33 | 2.92 > 3.08 |
| PACKAGE_ADVISORY | 8 | 3.75 > 4.12 | 4.12 > 4.12 | 3.62 > 3.50 | 3.88 > 4.12 | 3.88 > 4.00 | 3.00 > 3.88 |
| VENDOR_ADVISORY | 12 | 4.00 > 4.75 | 4.75 > 4.25 | 3.67 > 4.33 | 4.17 > 4.00 | 3.08 > 3.17 | 2.50 > 3.67 |
| NEWS_ARTICLE | 18 | 3.81 > 4.17 | 4.56 > 4.28 | 4.69 > 4.94 | 4.00 > 4.39 | 2.94 > 3.33 | 2.38 > 3.33 |
| EVENT_NOTICE | 6 | 4.00 > 4.33 | 4.00 > 3.67 | 3.33 > 5.00 | 4.00 > 4.50 | 3.33 > 3.67 | 2.67 > 3.50 |

(기준선의 `NEWS_ARTICLE` 은 16건, `EVENT_NOTICE` 는 3건만 채점됨: 나머지는 검증 탈락으로 글이 없음)

### 하네스가 더 나쁜 곳

- **coverage 가 0.27 낮다.** 요약이 276자에서 166자로, 핵심 정리가 3.3개에서 2.0개로 줄었다. 긴 권고(GitHub 권고, 여러 CVE 를 묶은 Ubuntu 공지)에서
  세부 원인·개별 CVE 영향이 빠졌다는 지적이 많다. "입력보다 확실히 짧게"와 "입력에 없는 말을 덧붙이지 않기"를 얻은 대가다.
  핵심 정리 규칙을 두 번 고쳐 봤지만(v3, v4) 4.16~4.18 에서 움직이지 않았다.
- `PACKAGE_ADVISORY` 의 actionGuide 가 3.62 > 3.50 으로 조금 낮다. 권고 본문의 "메인테이너가 이렇게 고치면 된다"를 대응 안내로 쓰지 않게 했는데,
  심사 모델은 그것도 조치로 본다.
- `VENDOR_ADVISORY` 의 koreanStyle 이 4.17 > 4.00. 긴 영어 제품 이름을 그대로 두어 문장이 딱딱하다는 지적이다 (이름을 바꾸지 않는 규칙과 충돌).

### 고친 순서

| 판 | 바꾼 것 | faithfulness | coverage | concision |
|---|---|---:|---:|---:|
| v1 | 글 종류별 안내 + 고쳐 쓰기 + 검증 규칙 수정 | 4.02* | 4.25* | 2.98* |
| v2 | 평가하는 맺음말 금지, 조사 붙여 쓰기, 입력보다 짧게, 핵심 정리는 요약에 없는 세부 값만 | 4.18 | 4.16 | 3.68 |
| v3 | 확실성 유지(가능성·의심·주장), 덧붙이기 금지, 글 자체를 가리키는 말 거부(검증) | 4.32 | 4.18 | 3.57 |
| v4 (현재) | 핵심 정리를 비우는 조건을 정형 공지로 좁힘 | 4.30 | 4.16 | 3.45 |

\* v1 은 채점 지시문을 고치기 전 숫자라 뒤의 판과 바로 비교할 수 없다 (제목에만 있는 사실을 근거 없음으로 채점하던 문제를 고침. 같은 채점 지시문으로 잰 기준선은 위 표의 3.92 / 4.43 / 2.65).
v3 과 v4 의 차이는 채점 오차 범위다.

## 4. 이 측정의 한계

- 표본 56건, 채점 1회다. 0.1~0.2 차이는 오차로 본다.
- KISA 공지는 표본에 없다. KISA 피드는 요약문을 주지 않아 요약 입력이 생기지 않는다 (`KISA_NOTICE` 규칙은 지어낸 예시로만 확인).
- Debian 권고는 입력이 주소뿐이라 요약 대상에서 뺐다 (README 3장).
- 로컬 생성(`claude -p`)과 운영 API 의 차이: 운영은 Messages API 의 구조화 출력·생각 끔·출력 토큰 상한 2500 이고,
  `claude -p` 는 `--json-schema`(도구 호출 방식)·`--effort low` 이며 출력 토큰 상한 옵션이 없다. 지시문·사용자 메시지·스키마·검증은 같다.
  하네스가 운영 API 에서 낸 숫자는 아직 재지 않았다.
