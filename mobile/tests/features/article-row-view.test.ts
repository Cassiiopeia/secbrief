/**
 * 목록 행 표시: 게시자 이름만, 번역 제목·원문 제목·번역 아이콘, 제목 아래 줄(한 줄 결론 우선), 메타(추정 게시일), 배지(색조), 썸네일, 주제 이름
 */
import { apiConfig } from '@/core/config/api-config';
import { toArticleRowView, toArticleSecondaryLine, toCvssSeverityTone, toTopicLabel } from '@/features/articles/article-row-view';

import { NOW, kisaItem, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

test('KEV 등재 항목도 메타는 게시자 이름만 (등재는 악용 중 배지로)', () => {
  const view = toArticleRowView({ ...nvdItem, isKev: true }, NOW);
  expect(view.metaLabel).toBe('NVD · 2026.11.23 09:12 · 2시간 전');
  expect(view.badges.map((badge) => badge.label)).toContain('악용 중');
});
test('번역 항목은 한국어 제목 + 원문 제목 + 번역 아이콘 (배지·글자 표시 없음)', () => {
  const view = toArticleRowView({ ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' }, NOW);
  expect(view.title).toBe('Next.js 심각 취약점');
  expect(view.originalTitle).toBe('Critical flaw in Next.js');
  expect(view.translatedMarkLabel).toBe('AI가 번역한 제목이에요');
  expect(view.badges.map((badge) => badge.label)).not.toContain('AI 번역');
  expect(view.accessibilityLabel).toContain('AI가 번역한 제목이에요');
});
test('번역 없으면 원문 제목만', () => {
  const view = toArticleRowView({ ...overseasNewsItem, translatedTitle: undefined }, NOW);
  expect(view.title).toBe(overseasNewsItem.title);
  expect(view.originalTitle).toBeUndefined();
  expect(view.translatedMarkLabel).toBeUndefined();
});
test('행 메타에 게시자와 절대·상대 날짜', () => {
  expect(toArticleRowView(kisaItem, NOW).metaLabel).toBe('KISA 보호나라 · 2026.11.23 09:12 · 2시간 전');
});
test('추정 게시일은 수집 표시', () => {
  expect(toArticleRowView({ ...kisaItem, isPublishedAtEstimated: true }, NOW).metaLabel).toBe(
    'KISA 보호나라 · 수집 2026.11.23 09:12 · 2시간 전',
  );
});
test('배지 순서: 긴급 > 악용 중 > 회사·제품 > 주제 > 종류 (일반 주제는 뺌, CVSS 는 게이지로, 번역은 아이콘으로)', () => {
  const view = toArticleRowView({ ...nvdItem, isKev: true, translatedTitle: '원격 코드 실행', subjectName: 'Example VPN' }, NOW);
  expect(view.badges.map((badge) => badge.label)).toEqual(['긴급', '악용 중', 'Example VPN', '원격 접속', '취약점']);
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
test('번역 항목도 보이는 메타는 게시자와 짧은 날짜만 (AI 번역 글자 없음)', () => {
  expect(toArticleRowView({ ...overseasNewsItem, translatedTitle: '번역 제목' }, NOW).compactMetaLabel).toBe('BleepingComputer · 2시간 전');
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
test('대표 이미지가 없으면 종류와 상관없이 그림 없음 (글만)', () => {
  expect(toArticleRowView(overseasNewsItem, NOW).imageUrl).toBeUndefined();
  expect(toArticleRowView(kisaItem, NOW).imageUrl).toBeUndefined();
  expect(toArticleRowView(nvdItem, NOW).imageUrl).toBeUndefined();
});
test('이미지 경로는 서버 기본 주소를 붙인 절대 주소로 (뉴스·공지·취약점 모두)', () => {
  expect(toArticleRowView({ ...overseasNewsItem, imagePath: '/api/v1/articles/4/image' }, NOW).imageUrl).toBe(
    `${apiConfig.baseUrl}/api/v1/articles/4/image`,
  );
  expect(toArticleRowView({ ...nvdItem, imagePath: '/api/v1/articles/2/image' }, NOW).imageUrl).toBe(`${apiConfig.baseUrl}/api/v1/articles/2/image`);
});
test('제목 아래 줄: 한 줄 결론이 있으면 원문 제목 대신 한 줄 결론', () => {
  const translatedItem = { ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' };
  expect(toArticleRowView({ ...translatedItem, oneLine: '예시 결론이에요' }, NOW).secondaryLine).toEqual({ text: '예시 결론이에요', tone: 'summary' });
  // 번역이 없는 국내 항목도 한 줄 결론이 있으면 보임
  expect(toArticleRowView({ ...kisaItem, oneLine: '예시 결론이에요' }, NOW).secondaryLine).toEqual({ text: '예시 결론이에요', tone: 'summary' });
});
test('제목 아래 줄: 한 줄 결론이 없으면 지금처럼 (번역 항목은 원문 제목, 그 밖은 없음)', () => {
  const translatedItem = { ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' };
  expect(toArticleRowView(translatedItem, NOW).secondaryLine).toEqual({ text: 'Critical flaw in Next.js', tone: 'reference' });
  expect(toArticleRowView(kisaItem, NOW).secondaryLine).toBeUndefined();
  expect(toArticleSecondaryLine(undefined, undefined)).toBeUndefined();
});
test('한 줄 결론이 있어도 상세 머리의 원문 제목은 그대로', () => {
  const view = toArticleRowView({ ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점', oneLine: '예시 결론이에요' }, NOW);
  expect(view.originalTitle).toBe('Critical flaw in Next.js');
});
test('읽기 도우미 문구: 한 줄 결론은 제목 다음에 AI 요약으로 읽음', () => {
  const view = toArticleRowView({ ...kisaItem, oneLine: '예시 결론이에요' }, NOW);
  expect(view.accessibilityLabel).toBe(`${kisaItem.title}, AI 요약, 예시 결론이에요, ${view.metaLabel}, 자세히 보기`);
});
