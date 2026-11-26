/**
 * 목록 행 표시: 게시자(KEV 등재), 번역 제목·원문 제목·AI 번역, 메타(추정 게시일), 배지(색조), 썸네일, 주제 이름
 */
import { apiConfig } from '@/core/config/api-config';
import { toArticleRowView, toCvssSeverityTone, toPublisherLabel, toTopicLabel } from '@/features/articles/article-row-view';

import { NOW, kevOnlyItem, kisaItem, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

test('KEV 등재 NVD 항목은 게시자 옆에 등재 표시', () => {
  expect(toPublisherLabel({ ...nvdItem, isKev: true })).toBe('NVD · CISA KEV 등재');
  // KEV 가 직접 올린 항목은 출처 코드로 알아봄 (게시자 이름과 무관)
  expect(toPublisherLabel({ ...kevOnlyItem, sourceCode: 'CISA_KEV', isKev: true })).toBe('CISA KEV');
});
test('번역 항목은 한국어 제목 + 원문 제목 + AI 번역 표시', () => {
  const view = toArticleRowView({ ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' }, NOW);
  expect(view.title).toBe('Next.js 심각 취약점');
  expect(view.originalTitle).toBe('Critical flaw in Next.js');
  expect(view.badges).toContainEqual({ label: 'AI 번역', tone: 'notice' });
});
test('번역 없으면 원문 제목만', () => {
  const view = toArticleRowView({ ...overseasNewsItem, translatedTitle: undefined }, NOW);
  expect(view.title).toBe(overseasNewsItem.title);
  expect(view.originalTitle).toBeUndefined();
  expect(view.badges.some((badge) => badge.tone === 'notice')).toBe(false);
});
test('행 메타에 게시자와 절대·상대 날짜', () => {
  expect(toArticleRowView(kisaItem, NOW).metaLabel).toBe('KISA 보호나라 · 2026.11.23 09:12 · 2시간 전');
});
test('추정 게시일은 수집 표시', () => {
  expect(toArticleRowView({ ...kisaItem, isPublishedAtEstimated: true }, NOW).metaLabel).toBe(
    'KISA 보호나라 · 수집 2026.11.23 09:12 · 2시간 전',
  );
});
test('배지 순서: 긴급 > 악용 중 > AI 번역 > 회사·제품 > 주제 > 종류 (일반 주제는 뺌, CVSS 는 게이지로)', () => {
  const view = toArticleRowView({ ...nvdItem, isKev: true, translatedTitle: '원격 코드 실행', subjectName: 'Example VPN' }, NOW);
  expect(view.badges.map((badge) => badge.label)).toEqual(['긴급', '악용 중', 'AI 번역', 'Example VPN', '원격 접속', '취약점']);
  expect(toArticleRowView(overseasNewsItem, NOW).badges.map((badge) => badge.label)).toEqual(['뉴스']);
});
test('CVSS 게이지: 점수는 원문 값, 버전 표기, 10점 대비 비율, 심각도 색조', () => {
  const cvss = toArticleRowView(nvdItem, NOW).cvss;
  expect(cvss).toMatchObject({ scoreLabel: '9.8', caption: 'CVSS 3.1', tone: 'critical' });
  expect(cvss?.ratio).toBeCloseTo(0.98);
  expect(toArticleRowView({ ...nvdItem, cvssVersion: undefined }, NOW).cvss?.caption).toBe('CVSS');
  expect(toArticleRowView(kisaItem, NOW).cvss).toBeUndefined();
});
test('화면에 보이는 메타는 게시자와 짧은 날짜', () => {
  expect(toArticleRowView(kisaItem, NOW).compactMetaLabel).toBe('KISA 보호나라 · 2시간 전');
  expect(toArticleRowView({ ...kisaItem, isPublishedAtEstimated: true }, NOW).compactMetaLabel).toBe('KISA 보호나라 · 수집 2시간 전');
});
test('번역 항목은 보이는 메타 끝에 AI 번역 표시', () => {
  expect(toArticleRowView({ ...overseasNewsItem, translatedTitle: '번역 제목' }, NOW).compactMetaLabel).toBe('BleepingComputer · 2시간 전 · AI 번역');
});
test('해시태그는 3개까지 (빈 값·중복 제외)', () => {
  const view = toArticleRowView({ ...overseasNewsItem, keywords: ['랜섬웨어', ' ', 'VPN', '랜섬웨어', '제로데이', '패치'] }, NOW);
  expect(view.hashtags.map((hashtag) => hashtag.keyword)).toEqual(['랜섬웨어', 'VPN', '제로데이']);
});
test('원문 주소와 읽기 도우미 문구', () => {
  const view = toArticleRowView(kisaItem, NOW);
  expect(view.originalUrl).toBe(kisaItem.originalUrl);
  expect(view.accessibilityLabel).toBe(`${kisaItem.title}, ${view.metaLabel}, 자세히 보기`);
});
test('새 주제 이름', () => {
  expect(toTopicLabel('supplyChain')).toBe('공급망');
  expect(toTopicLabel('policy')).toBe('정책·규제');
});
test('배지 색조: 긴급 urgent, 분류 neutral', () => {
  expect(toArticleRowView(nvdItem, NOW).badges.map((badge) => badge.tone)).toEqual(['urgent', 'neutral', 'neutral']);
});
test('CVSS 심각도 구간: 9.0 이상 critical, 7.0 이상 high, 4.0 이상 medium, 그 미만 low', () => {
  expect([9.0, 8.9, 7.0, 6.9, 4.0, 3.9].map(toCvssSeverityTone)).toEqual(['critical', 'high', 'high', 'medium', 'medium', 'low']);
});
test('뉴스 행은 이미지가 없어도 썸네일 자리(커버 그림)가 있다', () => {
  const thumbnail = toArticleRowView(overseasNewsItem, NOW).thumbnail;
  expect(thumbnail?.imageUrl).toBeUndefined();
  expect(thumbnail?.coverKind).toBeDefined();
  // 큰 카드 그림은 종류와 상관없이 항상 있음
  expect(toArticleRowView(kisaItem, NOW).cover.coverKind).toBeDefined();
});
test('이미지 경로는 서버 기본 주소를 붙인 절대 주소로', () => {
  const view = toArticleRowView({ ...overseasNewsItem, imagePath: '/api/v1/articles/4/image' }, NOW);
  expect(view.thumbnail?.imageUrl).toBe(`${apiConfig.baseUrl}/api/v1/articles/4/image`);
});
test('공지·취약점 행은 이미지가 있을 때만 썸네일', () => {
  expect(toArticleRowView(kisaItem, NOW).thumbnail).toBeUndefined();
  expect(toArticleRowView({ ...nvdItem, imagePath: '/api/v1/articles/2/image' }, NOW).thumbnail?.imageUrl).toBeDefined();
});
