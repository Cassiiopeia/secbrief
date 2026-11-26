/**
 * 알림함 목록 표시: 날짜 묶음(오늘·어제·날짜), 대표 기사 + 외 N건, 시각 표기, 공지 고정, 읽기 도우미 문구
 */
import type { AppNotificationSummary } from '@/data/models/app-notification';
import { toDayGroupLabel } from '@/core/format/notification-date-formatter';
import { toNotificationInboxRowView, toNotificationInboxSections } from '@/features/notifications/notification-inbox-view';

const NOW = new Date(2026, 10, 27, 12, 0);
const NO_BASELINE = { seenUpToId: 40, openedNotificationIds: new Set<number>() };

function toAlert(id: number, createdAt: Date, articleCount = 1): AppNotificationSummary {
  return {
    id,
    kind: 'ALERT',
    alertCategory: 'KEV',
    alertCategories: articleCount > 1 ? ['KEV', 'CRITICAL_CVE'] : ['KEV'],
    title: articleCount > 1 ? `긴급 보안 소식 ${articleCount}건이 들어왔어요` : '악용 중인 취약점이 확인됐어요',
    body: '푸시 본문',
    leadArticleTitle: '예시 VPN 인증 우회 취약점',
    articleCount,
    isPinned: false,
    createdAt,
    updatedAt: createdAt,
  };
}

test('날짜 묶음 이름: 오늘, 어제, 올해 날짜(요일), 지난해', () => {
  expect(toDayGroupLabel(new Date(2026, 10, 27, 0, 5), NOW)).toBe('오늘');
  expect(toDayGroupLabel(new Date(2026, 10, 26, 23, 59), NOW)).toBe('어제');
  expect(toDayGroupLabel(new Date(2026, 10, 24, 9, 0), NOW)).toBe('11월 24일 화요일');
  expect(toDayGroupLabel(new Date(2025, 11, 31, 9, 0), NOW)).toBe('2025년 12월 31일');
});

test('긴급 알림은 기기 날짜별로 묶고, 서버 순서를 지킨다', () => {
  const summaries = [
    toAlert(45, new Date(2026, 10, 27, 9, 0), 3),
    toAlert(44, new Date(2026, 10, 27, 0, 51)),
    toAlert(43, new Date(2026, 10, 26, 22, 51), 4),
    toAlert(42, new Date(2026, 10, 24, 12, 38)),
  ];
  const rowViews = summaries.map((summary) => toNotificationInboxRowView(summary, NO_BASELINE, NOW));
  const sections = toNotificationInboxSections('ALERT', summaries, rowViews, NOW);
  expect(sections.map((section) => [section.title, section.data.map((row) => row.id)])).toEqual([
    ['오늘', [45, 44]],
    ['어제', [43]],
    ['11월 24일 화요일', [42]],
  ]);
});

test('긴급 알림 한 줄: 배지, 대표 기사 + 외 N건, 오늘은 상대 표기·그 전은 시각, 새 글은 읽기 도우미에 먼저', () => {
  const todayRow = toNotificationInboxRowView(toAlert(45, new Date(2026, 10, 27, 9, 0), 3), NO_BASELINE, NOW);
  expect(todayRow).toMatchObject({
    badges: [
      { label: '악용 중', tone: 'exploited' },
      { label: '고위험', tone: 'critical' },
    ],
    detail: '예시 VPN 인증 우회 취약점 외 2건',
    timeLabel: '3시간 전',
    isNew: true,
  });
  expect(todayRow.accessibilityLabel.startsWith('새 알림, 악용 중, 고위험, 긴급 보안 소식 3건이 들어왔어요')).toBe(true);
  const olderRow = toNotificationInboxRowView(toAlert(30, new Date(2026, 10, 26, 22, 51)), NO_BASELINE, NOW);
  expect(olderRow).toMatchObject({ detail: '예시 VPN 인증 우회 취약점', timeLabel: '22:51', isNew: false });
});

test('대표 기사 제목이 없으면 푸시 본문', () => {
  const summary = { ...toAlert(45, NOW), leadArticleTitle: undefined };
  expect(toNotificationInboxRowView(summary, NO_BASELINE, NOW).detail).toBe('푸시 본문');
});

test('공지사항은 묶지 않고 날짜만, 고정은 표시와 읽기 도우미에', () => {
  const notice: AppNotificationSummary = {
    id: 9,
    kind: 'NOTICE',
    alertCategories: [],
    title: '서비스 점검 안내',
    body: '11월 30일 새벽 2시부터 30분 동안 점검해요.',
    articleCount: 0,
    isPinned: true,
    createdAt: new Date(2026, 10, 20, 10, 0),
    updatedAt: new Date(2026, 10, 20, 10, 0),
  };
  const rowView = toNotificationInboxRowView(notice, NO_BASELINE, NOW);
  expect(rowView).toMatchObject({ badges: [], isPinned: true, timeLabel: '2026.11.20', detail: notice.body });
  expect(rowView.accessibilityLabel.startsWith('고정된 공지, 서비스 점검 안내')).toBe(true);
  expect(toNotificationInboxSections('NOTICE', [notice], [rowView], NOW)).toEqual([{ key: 'notices', data: [rowView] }]);
  expect(toNotificationInboxSections('NOTICE', [], [], NOW)).toEqual([]);
});
