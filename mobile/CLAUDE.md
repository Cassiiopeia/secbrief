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

하단 탭 5개 + 탭 밖 스택 화면 6개. 기사를 누르면 브라우저가 아니라 앱 안 상세를 연다 (원문은 상세의 원문 보기 버튼).

| 라우트 | 화면 | 역할 |
|---|---|---|
| `(tabs)/index` | 홈 | 오늘의 브리핑: KISA 경보 상태, 주요 소식, 긴급, 숫자, 주제·키워드 바로가기, 최신 |
| `(tabs)/news` | 소식 | 뉴스·공지 피드, 카테고리·주제 칩 |
| `(tabs)/vulnerability` | 취약점 | 주요만 / 악용 중 / 전체, 출처 묶음 칩 |
| `(tabs)/explore` | 탐색 | 검색 들머리, 키워드 순위, 주제 동향, 행사 소식 |
| `(tabs)/settings` | 더보기 | 알림 설정, 출처, 문의, 약관, 앱 정보 |
| `articles/[articleId]` | 기사 상세 | AI 브리핑(한 줄 결론, 무슨 일, 왜 중요, 영향 대상, 대응 — 없으면 이전 요약 카드), 확인된 정보, 대응 모아보기(공식 대응 > 확인된 취약점 정보 > 관련 기사, 비면 함께 보면 좋은 소식), 원문 보기. `?focus=responseGuide` 면 대응 모아보기로 내려감 |
| `search` | 검색 | 글자·의미 검색, 요즘 키워드 |
| `browse/[browseKey]` | 모아 보기 | 주제·출처·키워드별 목록 |
| `notifications` | 알림함 | 알림 \| 공지사항 (`?segment=NOTICE`), 알림은 날짜별 묶음, 새 글 점. 탭 머리 종 아이콘·더보기에서 들어옴 |
| `notifications/[notificationId]` | 알림 상세 | 왜 알렸는지(종류별 설명) > 담긴 소식 (종류별 구역) > 소식마다 대응 안내 카드 (이렇게 대응해요, 공식 대응 첫 건, 대응 모아보기 N건 보기) |
| `notices/[noticeId]` | 공지사항 상세 | 제목, 날짜, 본문, 링크 버튼 |

- 버전·수치는 서버가 준 값만 보여 준다 (앱에서 만들지 않음). AI 가 쓴 부분에는 AI 표시를 붙인다.
- 알림(`expo-notifications`)은 `data/sources/device-notification-client.ts` 한 곳에서만 부른다.
- 알림을 누르면 `notification-target-route.ts` 가 열 곳을 정한다: `articleId` 기사 상세 > `notificationKind=NOTICE` 공지 상세 > `notificationId` 알림 상세 > `QUIET_HOURS_DIGEST` 알림함 > 이전 서버의 `targetTab`. 이동은 화면 스택이 준비된 뒤 (`use-is-root-navigator-ready`).
- 알림함 읽음 기록(본 번호·연 글)과 방해 금지 시간은 기기에만 둔다 (`secbrief.notificationReadState`, `secbrief.quietHours`). 방해 금지 시간은 알림 기기 등록(`PUT /api/v1/push-devices` 의 `quietHours`)으로 서버에도 보낸다.
- 알림 종류 문구(이름·배지·알린 이유)는 `features/notifications/alert-category-texts.ts` 가 단일 원본이다.
- 대응 모아보기 문구(구역 이름·설명·묶인 이유)는 `features/response-guide/response-guide-texts.ts` 가 단일 원본이다. 행은 `ResponseGuideRow` 하나를 기사 상세와 알림 상세가 함께 쓴다.
- 시각 선택기는 `@expo/ui/community/datetime-picker` (디자인 시스템 `SbTimePickerRow`): iOS 행 안 선택기, Android 시각 창, 웹은 글자만.

의존 방향: `core ← data ← features · design ← app`. 계층 밖은 `@/계층/...` 로만 import 한다.

## 푸시 알림 설정

앱 설정값은 `mobile/.env` 한 곳에 둔다 (커밋 안 함, 빌드 워크플로는 `ENV_FILE` 비밀값으로 만든다).

| 값 | 뜻 |
|---|---|
| `EXPO_PUBLIC_EAS_PROJECT_ID` | Expo 프로젝트 `@suhsaechan/secbrief` 의 ID. 없으면 알림을 쓸 수 없는 상태로 빌드된다 |
| `IOS_PUSH_APS_ENVIRONMENT` | iOS 푸시 권한 (`production`). 배포 프로파일에 Push Notifications 가 들어 있어야 한다 |

- 발송 인증정보는 Expo 프로젝트에 등록돼 있다: Android 는 FCM 발송 전용 서비스 계정 `secbrief-push-sender`, iOS 는 팀 APNs 키.
- 키를 바꾸면 Expo 프로젝트의 인증정보도 다시 등록한다 (앱 재빌드는 필요 없음).
- 서버는 `secbrief.push.expo.experience-id` 로 우리 프로젝트 토큰만 받는다 (`server/CLAUDE.md`).

## 앱 아이콘

원본은 `brand/app-icon-source.png` 한 장이다 (단색 배경 위 무늬, 1024 이상 정사각형). `assets/images/` 의 사본 7개는 스크립트가 만든다.

```bash
python3 brand/generate_platform_assets.py   # 레포 루트에서 실행 (Python 3 + Pillow)
```

| 사본 | 쓰는 곳 |
|---|---|
| `icon.png` | iOS 아이콘, Android 7 이하 아이콘 (1024, 알파 없음) |
| `android-icon-foreground.png` · `-background.png` · `-monochrome.png` | Android 적응형 아이콘, 테마 아이콘 |
| `splash-icon.png` | 시작 화면 (밝은·어두운 바탕 공용 둥근 타일) |
| `notification-icon.png` | Android 알림 아이콘 (흰 실루엣) |
| `favicon.png` | 웹 |

- 사본을 손으로 고치지 않는다. 아이콘을 바꿀 때는 원본 한 장을 바꾸고 스크립트를 다시 돌린다.
- 원본 배경은 `brand-colors.json` 의 `appIconBackground` 와 같은 계열의 단색이어야 한다 (아니면 스크립트가 거부). 배경색을 바꾸면 `app.json` 도 함께 바꾼다.
- iOS 는 `ios.icon` 없이 맨 위 `icon` 을 쓴다. 규격은 `tests/config/app-icon-assets.test.ts` 가 확인한다.

## 규칙 (tests/architecture 와 ESLint 가 강제)

- 화면(`app`, `features`)에 색·글꼴·글자 크기·간격·모서리·지속시간 숫자를 직접 쓰지 않는다. 토큰이 없으면 토큰을 먼저 추가한다.
- 움직임은 디자인 시스템 부품만 쓴다: 등장 `SbEntrance`·`SbListEntrance`, 조건부 구역 `SbReveal`, 누름 `SbPressable`, 로딩 `SbLoadingSkeleton`, 저장 확인 `showSbToast`. 화면에서 Reanimated·`withTiming`·`Easing`·`entering=` 직접 사용 금지. 값은 `sbMotion` 토큰 한 곳, 동작 줄이기가 켜져 있으면 바로 바뀐다.
- 빈 상태·오류 그림은 `SbEmptyState`(`illustration`)·`SbErrorState`(에러 코드로 연결 끊김/문제 생김 자동 선택)로만 쓴다. 원본은 `assets/illustrations/*.webp` (408px, 밝은·어두운 바탕 공용, 40KB 이하), 목록은 `sb-state-illustration-sources.ts` 한 곳. 새 그림은 `pro-image` 로 같은 세트 그림을 참고로 그린다.
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
