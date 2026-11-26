# 관리자 페이지, 설정 저장소(AES), AI 키 우선순위 설계 (#40)

작성일 2026-11-26. elum·passQL 의 관리자 구조를 secbrief 에 맞게 옮겼다.

## 1. 화면

| 경로 | 화면 | 내용 |
|---|---|---|
| `/admin/login` | 로그인 | 아이디 기준 5회 실패 시 15분 잠금 |
| `/admin` | 대시보드 | 오늘 현황 카드, 추이 4종(수집, AI 사용·비용, 이용, 알림 기기 등록), 출처 상태 |
| `/admin/sources` | 출처 관리 | 켜기·끄기, 수집 주기(5~1440분), 최근 실패 사유 |
| `/admin/settings` | 설정 | `ConfigKey` 묶음별 값 변경, 기본값 복원, 변경 이력 |
| `/admin/ai-keys` | AI 키 | 제공자 우선순위, 키 추가·삭제·순서·켜기·교체 |
| `/admin/ai-calls` | AI 호출 | 호출 기록, 오늘·이번 달 합계 |
| `/admin/audit` | 관리자 기록 | 누가 무엇을 바꿨는지 |
| `/admin/password` | 비밀번호 변경 | 10자 이상, 영문·숫자 포함 |

- Thymeleaf 서버 렌더링 + DaisyUI 5 + Tailwind 4 브라우저 런타임 + Chart.js. 전부 로컬 파일이라 외부 CDN 에 의존하지 않는다.
- 레이아웃 조각 `admin/fragments/admin-layout :: page(title, activeMenu, content)` 를 모든 화면이 쓴다.

## 2. 인증

- 보안 체인 2개: 관리자(`/admin/**`, 세션 폼 로그인, CSRF 켬) / 공개 API(`/api/**`, `/actuator/health`, 세션 없음). 그 밖의 경로는 403.
- 초기 계정은 운영 설정 `secbrief.admin.accounts[]` 에서 기동 시 만든다 (없는 아이디만, BCrypt). 레포에는 계정 정보를 두지 않는다.
- 세션 쿠키는 HttpOnly, SameSite=Lax, 운영은 Secure.

## 3. 설정 저장소와 암호화

- `system_config` + `system_config_history`. `ConfigKey` enum 이 단일 원본(묶음, 값 종류, 범위, 기본값).
- 값을 읽는 순서: DB > 운영 설정(yml) > enum 기본값. 30초 캐시.
- 비밀 종류는 AES-256-GCM 으로 암호화: `enc:v1:` + Base64(nonce 12바이트 ‖ 암호문 ‖ tag), AAD 는 설정 키 이름 (다른 행으로 옮기면 복호화 실패).
- 마스터 키는 운영 설정 `secbrief.secret.master-key`. 없으면 서버는 뜨고 비밀 설정 저장만 거부한다.

## 4. AI 키 우선순위와 fallback

- `ANTHROPIC_API_KEYS`: JSON 배열을 통째로 암호화, 배열 순서가 우선순위. 최대 10개.
- DB 에 켜진 키가 없으면 운영 설정의 `secbrief.ai.anthropic-api-key` (쉼표로 여러 개) 를 쓴다.
- 다음 키로 넘기는 오류: 401·402·403·429·529, 5xx, 연결 실패·시간 초과, 400 + `credit balance`. 실패한 키는 5분 쉬게 뒤로 미룬다.
- 제공자 우선순위 `AI_PROVIDER_PRIORITY` (예: `anthropic,ollama`).
- 호출마다 `ai_call_log` 에 종류·제공자·모델·키 별명·성공 여부·지연·토큰·추정 비용을 남긴다 (입력 글·키 값은 남기지 않음).
- 일일 예산 `AI_DAILY_BUDGET_USD`, 제목 분석은 최신 소식부터.

## 5. 이용 추이

- 개인 식별값 없이 화면 묶음별 요청 건수만 하루 단위로 센다 (`api_usage_daily`). 요청 경로에서는 메모리 카운터만 올리고 1분마다 저장한다.

## 6. 설정 한 곳 원칙

- 서버 비밀값은 `application-prod.yml` 한 파일(Secret `APPLICATION_PROD_YML`), 앱 설정은 `.env` 한 파일(Secret `ENV_FILE`). 워크플로가 값을 따로 넘기지 않는다.
- 테스트 APK 워크플로는 `deploy` 푸시에서도 돌아 Firebase App Distribution 에 올린다.

## 7. 하지 않는 것

- 관리자 권한 단계(조회 전용 계정), 여러 서버 사이의 잠금·쿨다운 공유, 관리자 화면의 HTML 오류 페이지.
