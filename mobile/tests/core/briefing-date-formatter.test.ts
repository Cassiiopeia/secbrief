/**
 * 날짜 표기: 전체 표기(절대 날짜·시각 · 상대 표기), 짧은 표기, 날짜만 있는 게시일
 * 기기 시간대 기준이라 로컬 Date 생성자로 만든다 (날짜만 있는 값은 UTC 로 만들고 모양만 확인)
 */
import {
  isDateOnly,
  toArticleDateLabel,
  toCompactDateLabel,
  toDateTimeLabel,
  toRelativeDay,
} from '@/core/format/briefing-date-formatter';

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
test('짧은 표기: 7일 안은 상대 표기, 그보다 오래되면 날짜', () => {
  expect(toCompactDateLabel(new Date(2026, 10, 23, 9, 12), NOW)).toBe('2시간 전');
  expect(toCompactDateLabel(new Date(2026, 10, 17, 9, 12), NOW)).toBe('6일 전');
  expect(toCompactDateLabel(new Date(2026, 10, 16, 9, 12), NOW)).toBe('2026.11.16');
});

describe('날짜만 있는 게시일', () => {
  const utcMidnight = new Date(Date.UTC(2026, 10, 22, 0, 0, 0));
  const seoulMidnight = new Date(Date.UTC(2026, 10, 21, 15, 0, 0));

  test('UTC 자정·서울 자정(15:00Z)만 날짜만 있는 값으로 본다', () => {
    expect(isDateOnly(utcMidnight)).toBe(true);
    expect(isDateOnly(seoulMidnight)).toBe(true);
    expect(isDateOnly(new Date(Date.UTC(2026, 10, 22, 15, 0, 1)))).toBe(false);
    expect(isDateOnly(new Date(Date.UTC(2026, 10, 22, 0, 12, 0)))).toBe(false);
    expect(isDateOnly(new Date(Date.UTC(2026, 10, 22, 9, 0, 0)))).toBe(false);
  });
  test('시각(00:00)을 붙이지 않고 상대 표기는 날 단위', () => {
    for (const publishedAt of [utcMidnight, seoulMidnight]) {
      expect(toArticleDateLabel(publishedAt, NOW)).toMatch(/^2026\.11\.2\d · \d일 전$/);
      expect(toCompactDateLabel(publishedAt, NOW)).toMatch(/^\d일 전$/);
    }
  });
  test('날 단위 상대 표기: 같은 날·미래는 오늘, 그 외 달력 날짜 차이', () => {
    expect(toRelativeDay(new Date(2026, 10, 23, 0, 0), NOW)).toBe('오늘');
    expect(toRelativeDay(new Date(2026, 10, 24, 0, 0), NOW)).toBe('오늘');
    expect(toRelativeDay(new Date(2026, 10, 22, 23, 0), NOW)).toBe('1일 전');
    expect(toRelativeDay(new Date(2026, 10, 20, 0, 0), NOW)).toBe('3일 전');
  });
  test('수집 시각 표기는 자정이어도 시각을 붙인다', () => {
    expect(toDateTimeLabel(utcMidnight, NOW)).toMatch(/^2026\.11\.2\d \d{2}:\d{2} · /);
  });
});
