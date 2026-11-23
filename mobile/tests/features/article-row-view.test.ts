/**
 * 목록 행 표시: 게시자(KEV 등재), 번역 제목·원문 제목·AI 번역, 메타(추정 게시일), 배지, 주제 이름
 */
import { toArticleRowView, toPublisherLabel, toTopicLabel } from '@/features/articles/article-row-view';

import { NOW, kevOnlyItem, kisaItem, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

test('KEV 등재 NVD 항목은 게시자 옆에 등재 표시', () => {
  expect(toPublisherLabel({ ...nvdItem, isKev: true })).toBe('NVD · CISA KEV 등재');
  expect(toPublisherLabel({ ...kevOnlyItem, publisherName: 'CISA KEV', isKev: true })).toBe('CISA KEV');
});
test('번역 항목은 한국어 제목 + 원문 제목 + AI 번역 표시', () => {
  const view = toArticleRowView({ ...overseasNewsItem, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' }, NOW);
  expect(view.title).toBe('Next.js 심각 취약점');
  expect(view.originalTitle).toBe('Critical flaw in Next.js');
  expect(view.translationNoticeLabel).toBe('AI 번역');
});
test('번역 없으면 원문 제목만', () => {
  const view = toArticleRowView({ ...overseasNewsItem, translatedTitle: undefined }, NOW);
  expect(view.title).toBe(overseasNewsItem.title);
  expect(view.originalTitle).toBeUndefined();
  expect(view.translationNoticeLabel).toBeUndefined();
});
test('행 메타에 게시자와 절대·상대 날짜', () => {
  expect(toArticleRowView(kisaItem, NOW).metaLabel).toBe('KISA 보호나라 · 2026.11.23 09:12 · 2시간 전');
});
test('추정 게시일은 수집 표시', () => {
  expect(toArticleRowView({ ...kisaItem, isPublishedAtEstimated: true }, NOW).metaLabel).toBe(
    'KISA 보호나라 · 수집 2026.11.23 09:12 · 2시간 전',
  );
});
test('배지: 지역·종류·주제·CVSS·긴급', () => {
  expect(toArticleRowView(nvdItem, NOW).badgeLabels).toEqual(['해외', '취약점', '원격 접속', 'CVSS 9.8 (v3.1)', '긴급']);
});
test('원문 주소와 읽기 도우미 문구', () => {
  const view = toArticleRowView(kisaItem, NOW);
  expect(view.originalUrl).toBe(kisaItem.originalUrl);
  expect(view.accessibilityLabel).toBe(`${kisaItem.title}, ${view.metaLabel}, 원문 열기`);
});
test('새 주제 이름', () => {
  expect(toTopicLabel('supplyChain')).toBe('공급망');
  expect(toTopicLabel('policy')).toBe('정책·규제');
});
