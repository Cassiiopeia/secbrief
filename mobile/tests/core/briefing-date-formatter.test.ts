/**
 * 날짜 표기: 절대 날짜 + 상대 표기 (기기 시간대 기준, 테스트는 로컬 Date 생성자로 만든다)
 */
import { toArticleDateLabel } from '@/core/format/briefing-date-formatter';

const NOW = new Date(2026, 10, 23, 12, 0);

test('절대 날짜 + 시간 전', () => {
  expect(toArticleDateLabel(new Date(2026, 10, 23, 9, 12), NOW)).toBe('2026.11.23 09:12 · 2시간 전');
});
test('1분 미만은 방금', () => {
  expect(toArticleDateLabel(new Date(2026, 10, 23, 11, 59, 30), NOW)).toBe('2026.11.23 11:59 · 방금');
});
test('60분 미만은 분 전', () => {
  expect(toArticleDateLabel(new Date(2026, 10, 23, 11, 15), NOW)).toBe('2026.11.23 11:15 · 45분 전');
});
test('하루 넘으면 일 전', () => {
  expect(toArticleDateLabel(new Date(2026, 10, 20, 12, 0), NOW)).toBe('2026.11.20 12:00 · 3일 전');
});
test('미래 시각은 방금', () => {
  expect(toArticleDateLabel(new Date(2026, 10, 23, 13, 0), NOW)).toBe('2026.11.23 13:00 · 방금');
});
