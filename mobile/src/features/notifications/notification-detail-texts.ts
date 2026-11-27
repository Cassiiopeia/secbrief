import type { AppNotificationKind } from '@/data/models/app-notification';

/** 알림함 상세 찾지 못함 안내: 제목 + 설명 + 에러 코드 + 돌아갈 알림함 칸 */
export interface NotificationNotFoundView {
  title: string;
  description: string;
  /** 서버 에러 코드 그대로 (기사 상세 찾지 못함과 같은 RESOURCE_NOT_FOUND, #119) */
  errorCode: string;
  inboxSegment: 'ALERT' | 'NOTICE';
}

// 종류별 머리 제목, 찾지 못했을 때 안내 (단일 원본)
const DETAIL_TEXTS: Record<AppNotificationKind, { headerTitle: string; notFoundTitle: string; notFoundDescription: string }> = {
  ALERT: { headerTitle: '알림', notFoundTitle: '보관 기간이 지난 알림이에요', notFoundDescription: '알림은 30일 동안 보관해요.' },
  NOTICE: { headerTitle: '공지사항', notFoundTitle: '내려간 공지사항이에요', notFoundDescription: '알림함에서 다른 공지사항을 볼 수 있어요.' },
  DIGEST: { headerTitle: '아침 요약', notFoundTitle: '보관 기간이 지난 요약이에요', notFoundDescription: '요약은 30일 동안 보관해요.' },
};

/** 종류 > 상세 머리 제목 */
export function toNotificationDetailHeaderTitle(kind: AppNotificationKind): string {
  return DETAIL_TEXTS[kind].headerTitle;
}

/** 종류 + 서버 에러 코드 > 찾지 못함 안내 (공지는 공지사항 칸, 그 밖은 알림 칸으로 돌아감) */
export function toNotificationNotFoundView(kind: AppNotificationKind, errorCode: string): NotificationNotFoundView {
  return {
    title: DETAIL_TEXTS[kind].notFoundTitle,
    description: DETAIL_TEXTS[kind].notFoundDescription,
    errorCode,
    inboxSegment: kind === 'NOTICE' ? 'NOTICE' : 'ALERT',
  };
}
