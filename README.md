# 시큐브리프 (secbrief)

국내 서비스 운영자와 보안 담당자를 위한 보안 뉴스·취약점 알림 앱.
KISA 등 공개 보안 공지를 모아 한국어로 정리하고, 내가 쓰는 제품에 해당하는 취약점을 알려 준다.

## 구조

| 폴더 | 역할 |
|---|---|
| `mobile/` | React Native(Expo) 앱 (Android, iOS) |
| `server/` | Spring Boot 서버 (수집, 정리, 알림, API) |
| `brand/` | 브랜드 색·아이콘 원본 |
| `docs/` | 기획·설계 문서 |

작업 규칙은 [CLAUDE.md](./CLAUDE.md), 스택별 규칙은 `mobile/CLAUDE.md`, `server/CLAUDE.md`.

<!-- AUTO-VERSION-SECTION: DO NOT EDIT MANUALLY -->
## 최신 버전 : v0.7.1 (2026-11-26)
