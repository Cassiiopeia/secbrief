# 알림 줄이기 · 분류 태그 카탈로그 · 아침 요약 · 오늘 새 소식 (#93, #97)

시안 D + A + C + F 를 고른 결정(#93 댓글 2026-11-27)과 시안 HTML(`docs/projectops/ui-design/20261127_93_알림_줄이기_오픈소스_정리/`)이 화면 원본이다.
이 문서는 서버·앱이 함께 지키는 **데이터 구조와 API 계약**이다.

## 1. 핵심 결정

| 주제 | 결정 |
|---|---|
| 분류 목록 | 기술 분야·생태계·제품·제품 묶음은 **서버 DB(`catalog_tag`)** 가 단일 원본. 관리자 화면에서 추가·이름 바꾸기·순서·숨김. Java enum·앱 상수로 값을 두지 않음 |
| 코드 | 항목마다 바뀌지 않는 `code`(소문자·숫자·`-`). 기사 태그·기기 관심 기술은 code 만 저장. 이름을 바꿔도 code 는 같음 |
| 지운(숨긴) 항목 | 관심 기술에 남은 모르는 code 는 **조용히 무시**(알림 판정·목록 필터에서 빠짐). 기기가 다음에 설정을 저장하면 서버가 살아 있는 code 만 남김. 앱은 모르는 code 를 그리지 않음 |
| 분류 순서 | NVD CPE 제조사·제품 / KEV 제조사·제품 / GitHub 권고 패키지 > 제품 사전 일치 > 분야 낱말 일치 > AI 가 고른 분야(제목 분석 같은 호출, 지금 카탈로그 목록을 스키마 enum 으로) > `other` |
| 주요(isNotable) | KEV 등재 ∨ 널리 쓰는 제품 일치 ∨ (CVSS ≥ 9.0 ∧ AI 판단 끝 ∧ AI 중요도 ≠ 일반) ∨ AI 중요도 헤드라인 |
| 바로 알림 | 악용 중(KEV) · KISA 긴급 · (관심 일치 ∧ 긴급) · (그 밖의 고위험 켬 ∧ CVSS 9.0 이상 고위험). 관심을 안 고르면 악용 중 + KISA 긴급만 |
| 아침 요약 | 관심 일치인데 긴급이 아닌 소식. 기기 설정 `IMMEDIATE`면 바로, `DIGEST`(기본)면 기기 시각 08:00(바꿀 수 있음) 첫 알림 틱에 1번 |
| #97 | 알림 후보는 AI 판단이 끝난 기사만. 수집 후 60분이 지나도 AI 가 못 했으면 규칙만으로(고위험은 널리 쓰는 제품 일치일 때만) |

## 2. 데이터

### `catalog_tag` (V0.9.0)

| 열 | 뜻 |
|---|---|
| `kind` | `FIELD` 기술 분야, `ECOSYSTEM` 오픈소스 생태계, `PRODUCT_GROUP` 관심 기술 화면의 인기 묶음, `PRODUCT` 제품·회사·패키지 |
| `code` | 바뀌지 않는 값, kind 안에서 유일 |
| `name`, `short_name` | 화면 이름 / 칩·태그용 짧은 이름 |
| `group_code` | PRODUCT 의 인기 묶음 (없으면 검색으로만 찾음) |
| `field_code` | PRODUCT 의 기술 분야 (제품이 맞으면 분야도 정해짐) |
| `match_terms` | 분류에 쓰는 낱말 (쉼표 구분, 소문자 비교, 영문은 낱말 경계·한글은 부분 일치, `~` 로 시작하면 약한 낱말). ECOSYSTEM 은 GitHub `ecosystem` 원문 값(`pip`, `composer`) |
| `is_widely_used` | 주요 판정의 "널리 쓰는 제품" (PRODUCT 만) |
| `is_visible` | 숨기면 API·분류·관심 판정에서 빠짐 |
| `display_order` | 칩·묶음 순서 |

### `article` 새 열

`field_code`, `ecosystem_code`, `package_name`, `product_code`, `ai_field_code`(AI 가 고른 분야 원본), `field_source`(`PRODUCT`·`KEYWORD`·`AI`·`NONE`), `is_notable`, `classified_at`(null 이면 분류 대기).
`urgent_notified_at` 은 이제 **알림 처리 시각**(긴급 아님도 관심 일치 판정을 거침). 마이그레이션 때 긴급이 아닌 기존 행은 처리됨으로 채움.

### 기기 (`push_device`)

`alert_delivery_mode`(`IMMEDIATE`|`DIGEST`, 기본 `DIGEST`), `digest_time`(기본 08:00), `last_digest_date`(기기 날짜). 관심 기술은 `push_device_interest(push_device_id, kind, code)`.
기존 `CRITICAL_CVE` 켬 = "그 밖의 고위험 취약점" 켬. 시안 기본값(꺼짐)에 맞춰 마이그레이션 때 기존 기기의 `CRITICAL_CVE` 를 끔 (알림이 너무 많다는 요청).
기기 행은 켠 종류도 관심 기술도 없을 때만 지움.

아침 요약 대기: `push_device_digest_item(push_device_id, article_id, queued_at)`. 보낸 요약은 `notification` kind `DIGEST`(기기 1대 전용, `push_device_id` 채움) + `notification_article`(종류 `INTEREST`).
관심만 맞은 바로 알림이 여러 건이면 기기 전용 `ALERT` 기록을 만듦. **알림함 목록은 기기 전용 기록을 보여 주지 않음**(`push_device_id is null` 만).

## 3. API (응답은 모두 `ApiResponse.data`)

### `GET /api/v1/catalog/tags`

```json
{
  "version": "a1b2c3",
  "tags": [
    { "kind": "FIELD", "code": "os", "name": "운영체제·시스템 패키지", "shortName": "운영체제",
      "groupCode": null, "fieldCode": null, "displayOrder": 10, "isWidelyUsed": false }
  ]
}
```

- 보이는 항목만, `kind` > `displayOrder` 순. 헤더 `ETag: "<version>"`, `If-None-Match` 가 같으면 304 (본문 없음).
- `version` 은 항목 내용이 바뀌면 바뀜. 앱은 받은 목록을 기기에 저장하고 앱 시작·앞으로 올 때 다시 물음.

### `GET /api/v1/articles` 새 조건 (모두 선택)

| 이름 | 뜻 |
|---|---|
| `sourceCodes` | 출처 여러 곳 (쉼표). `sourceCode` 와 같이 오면 둘 다 만족 |
| `fieldCode`, `ecosystemCode` | 분야 1개 / 생태계 1개 |
| `isNotable` | `true` 면 주요만, `false` 면 주요 아닌 것만 |
| `interestProductCodes`, `interestFieldCodes`, `interestEcosystemCodes` | "내 기술": 셋 중 하나라도 맞으면 (쉼표, 셋 다 비면 조건 없음) |
| `collectedSince` | ISO 시각, 이 시각 이후 수집분 ("오늘") |

`ArticleResponse` 새 필드: `fieldCode`, `ecosystemCode`, `packageName`, `productCode` (없으면 null), `isNotable`.

### `GET /api/v1/articles/new-counts?timeZone=Asia/Seoul`

기기 날짜 기준 오늘 0시 이후 **수집한 공개 소식** 건수.

```json
{
  "since": "2026-11-26T15:00:00Z", "lastCollectedAt": "2026-11-26T22:36:00Z", "totalCount": 39,
  "exploitedCount": 0, "kisaCount": 0, "newsCount": 8,
  "fieldCounts": [ { "code": "web-app", "count": 11, "sampleNames": ["Budibase", "ToolJet"] } ],
  "ecosystemCounts": [ { "code": "npm", "count": 5, "sampleNames": ["appium"] } ],
  "sourceCounts": [ { "sourceCode": "NVD", "count": 12 } ]
}
```

분야·생태계는 뉴스가 아닌 항목만. `sampleNames` 는 제품·패키지·제목 속 이름 앞 3개(원문 표기). 0건 분야는 빼고, 앱이 카탈로그 기준으로 "없음"을 그림.

### `PUT /api/v1/push-devices` (기존 + 선택 필드 2개)

```json
{ "pushToken": "...", "platform": "IOS", "enabledAlertCategories": ["KEV", "KISA_URGENT"],
  "quietHours": { "isEnabled": true, "startTime": "23:00", "endTime": "07:00", "timeZone": "Asia/Seoul" },
  "interests": { "productCodes": ["fortinet"], "fieldCodes": ["network"], "ecosystemCodes": ["maven"] },
  "digest": { "deliveryMode": "DIGEST", "digestTime": "08:00" } }
```

없는 필드(이전 앱)는 저장값 유지. 응답에 `interests`, `digest` 항상 (`interests` 는 살아 있는 code 만).
`CRITICAL_CVE` = 그 밖의 고위험 취약점.

### `POST /api/v1/push-devices/alert-estimate`

요청: `{ enabledAlertCategories, interests, digest, timeZone }` (기기 등록 없이 계산만).
응답: `{ "date": "2026-11-26", "immediatePushCount": 1, "immediateArticleCount": 2, "digestArticleCount": 6, "digestPushCount": 1, "currentPushCount": 7 }`
어제(기기 날짜) 수집분을 알림 규칙으로 다시 돌림. 바로 알림 횟수는 실제 알림 틱(5분) 단위로 셈. `currentPushCount` 는 어제 실제로 보낸 공통 알림 수.

### 알림 상세 `GET /api/v1/notifications/{id}` (DIGEST)

`kind: "DIGEST"`, 기존 필드 + `digest`:

```json
"digest": { "articleCount": 20, "notableArticles": [ArticleResponse],
            "fieldSections": [ { "fieldCode": "web-app", "articles": [ArticleResponse] } ] }
```

`fieldSections` 는 건수 많은 순, 분야 없는 기사는 `other`. 다른 종류는 `digest: null`.
푸시 data: `notificationKind` 에 `DIGEST` 추가 (앱은 알림 상세를 엶). 알림 종류 `INTEREST`(관심 기술) 추가, 묶음 순서 KEV > KISA_URGENT > CRITICAL_CVE > INTEREST.

## 4. 알림 틱 (5분)

1. 후보: 알림 처리 전 + (AI 판단 끝 ∨ 수집 후 60분 지남) + 분류 끝.
2. 공통 기록(알림함 목록에 보임): 악용 중·KISA 긴급·고위험(알릴 만한 것) — 지금과 같음.
3. 기기마다 후보를 `IMMEDIATE`·`DIGEST`·`NONE` 으로 나눔 (`AlertRoutingPolicy` 한 곳). 바로 알림은 방해 금지면 지금처럼 보류.
4. 아침 요약: 기기 시각이 요약 시각을 지났고 오늘 아직 안 보냈고 방해 금지가 아니면 대기분을 분야별로 모아 1건 ("오늘 아침 요약: 관심 분야 소식 N건"). 48시간 넘은 대기분은 버림.

## 5. 기존 항목 채우기

분류 틱 `ArticleClassificationScheduler`(1분, `secbrief.classification.*`)가 `classified_at is null` 인 행을 200건씩 규칙으로 분류한다. 마이그레이션 뒤 기존 행 전체가 대상이라 배포 뒤 첫 몇 틱이 곧 1회 백필이다.
규칙으로 분야를 못 정한 예전 AI 완료 행만 AI 대기로 돌려 제목 분석(같은 호출, 분야 포함)이 1번 고른다. 건수는 로그 `[article classify] classified : targets=…, byProduct=…, byKeyword=…, byAi=…, other=…, aiRequested=…`.
이미 분류된 행은 고르지 않아 다시 돌려도 같다. 관리자 화면 "기사 전체 다시 분류"는 `classified_at` 을 비워 차례로 다시 돌린다 (AI 는 다시 부르지 않음, 알림도 다시 보내지 않음).

## 6. #97 확인 결과 (운영 DB 읽기 전용, 2026-11-27)

- 11.26 12:38 ~ 11.27 02:52 알림 처리된 긴급 기사 30건 중 지금 AI 가 "일반"인 13건은, 1건(TOTOLINK, 12:58)을 빼고 모두 **알림 틱 직전에 AI 제목 분석이 끝난 뒤** 처리됐다 (`ai_call_log` 호출 시각 < 처리 시각, 수집 후 1~3분).
  당시 코드(`isAlertWorthy`)는 AI 가 일반으로 본 고위험을 보내지 않고 **처리 표시만** 했으므로, 이 12건은 실제로 나가지 않은 것으로 보인다. `urgent_notified_at` 이 "보냄"이 아니라 "처리함"이라 건수가 부풀려 보였다.
- 판단 전에 나간 것으로 보이는 건 TOTOLINK 1건이다: 첫 배포 직후 밀린 AI 대기열 때문에 10분 대기를 넘겨 AI 판단 없이 알림 대상이 됐다 (당시 `ai_call_log` 가 없어 호출 시각은 확인 못 함).
- 그 기간 알림함 기록(`notification`)은 V0.8.1 배포(11.27 04:08) 전이라 실제 발송 목록은 남아 있지 않다.
- 고친 것: 대기 10분 > 60분, 그동안 AI 판단·분류가 끝난 기사만 후보, 60분이 지나면 규칙만으로(널리 쓰는 제품일 때만 고위험 알림).

## 7. 하지 않는 것

- 계정·기기 간 관심 기술 동기화 없음 (기기 단위).
- 기사 본문을 읽어 분류하지 않음 (제목·공개 사실 값·CPE·패키지 이름만).
- 영향·조치 버전을 분류에 쓰지 않음.
