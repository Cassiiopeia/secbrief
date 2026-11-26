# mobile — 시큐브리프 앱 (React Native · Expo)

Expo SDK 58, TypeScript, Expo Router. 루트 [CLAUDE.md](../CLAUDE.md)의 공통 규칙을 함께 따른다.
Expo 는 SDK 마다 API 가 바뀐다. Expo·React Native API 를 쓰기 전에 `package.json` 의 버전을 보고
`https://docs.expo.dev/versions/v58.0.0/` 문서를 확인한다. 기억으로 쓰지 않는다.

## 구조

```
mobile/
├── app.json            앱 이름·패키지 ID·아이콘·시작 화면 (네이티브 폴더는 생성물, 손대지 않음)
├── assets/             글꼴(Pretendard, D2Coding), 아이콘
├── src/
│   ├── app/            라우트만 (Expo Router, 파일 = 화면). 얇게 유지
│   ├── features/       화면 조립 · 뷰 상태 변환 · 조회 훅 (기능별 폴더)
│   ├── design/         디자인 시스템: tokens, theme, components. 진입점은 index.ts
│   ├── data/           models(형태), sources(서버·예시 데이터)
│   └── core/           config, error, format (아무 계층도 모름)
└── tests/architecture/ 규칙 테스트
```

## 화면 구성

하단 탭 5개 + 탭 밖 스택 화면 3개. 기사를 누르면 브라우저가 아니라 앱 안 상세를 연다 (원문은 상세의 원문 보기 버튼).

| 라우트 | 화면 | 역할 |
|---|---|---|
| `(tabs)/index` | 홈 | 오늘의 브리핑: KISA 경보 상태, 주요 소식, 긴급, 숫자, 주제·키워드 바로가기, 최신 |
| `(tabs)/news` | 소식 | 뉴스·공지 피드, 카테고리·주제 칩 |
| `(tabs)/vulnerability` | 취약점 | 주요만 / 악용 중 / 전체, 출처 묶음 칩 |
| `(tabs)/explore` | 탐색 | 검색 들머리, 키워드 순위, 주제 동향, 행사 소식 |
| `(tabs)/settings` | 더보기 | 알림 설정, 출처, 문의, 약관, 앱 정보 |
| `articles/[articleId]` | 기사 상세 | AI 요약, 핵심, 대응, 확인된 정보, 관련 소식, 원문 보기 |
| `search` | 검색 | 글자·의미 검색, 요즘 키워드 |
| `browse/[browseKey]` | 모아 보기 | 주제·출처·키워드별 목록 |

- 버전·수치는 서버가 준 값만 보여 준다 (앱에서 만들지 않음). AI 가 쓴 부분에는 AI 표시를 붙인다.
- 알림(`expo-notifications`)은 `data/sources/device-notification-client.ts` 한 곳에서만 부른다.

의존 방향: `core ← data ← features · design ← app`. 계층 밖은 `@/계층/...` 로만 import 한다.

## 규칙 (tests/architecture 와 ESLint 가 강제)

- 화면(`app`, `features`)에 색·글꼴·글자 크기·간격·모서리·지속시간 숫자를 직접 쓰지 않는다. 토큰이 없으면 토큰을 먼저 추가한다.
- 글자는 `SbText` 만 쓴다 (한글 낱말 단위 줄바꿈이 여기 한 곳에 있다). `Text` 직접 사용 금지.
- 다크 판정은 `useSbTheme` 한 곳. 테마 색이 필요한 스타일은 `useSbStyles(createStyles)`.
- `style` 에 객체를 직접 쓰지 않는다. `StyleSheet` 로 이름을 붙인다.
- 화면은 디자인 시스템을 `@/design` 진입점으로만 import 한다.
- 색을 바꿀 때는 `brand/brand-colors.json`, `src/design/tokens/sb-colors.ts`, `app.json` 을 함께 바꾼다.
- 글꼴 이름은 굵기마다 따로다 (`sbFontFamily`). `fontWeight` 로 굵기를 고르지 않는다.
- 화면 문구는 한국어 해요체. 오류 화면에는 에러 코드를 함께 보여 준다.
- 패키지는 `npx expo install <이름>` 으로만 추가한다 (SDK 에 맞는 버전).

## 검증

```bash
npx tsc --noEmit          # 타입 검사
npx eslint .              # 린트 (expo lint 는 ESLint 9 를 거부해 직접 실행)
npx jest                  # 규칙 테스트
npx expo export --platform web   # 묶이는지 확인 (결과 dist/)
npx expo start --web      # 웹 미리보기
```
