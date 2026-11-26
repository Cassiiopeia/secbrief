import type { AlertCategory } from './alert-category';
import type { BriefingItem } from './briefing-item';
import type { ResponseGuideItem } from './response-guide';

/**
 * 알림 기록 종류 3개 (서버 NotificationKind 이름): ALERT 긴급 알림 기록, NOTICE 공지사항, DIGEST 아침 요약(기기 1대 전용, #93)
 * 알림함 목록 칸은 ALERT·NOTICE 2개 (아침 요약은 푸시로만 엶)
 */
export const APP_NOTIFICATION_KINDS = ['ALERT', 'NOTICE', 'DIGEST'] as const;
export type AppNotificationKind = (typeof APP_NOTIFICATION_KINDS)[number];
export const INBOX_NOTIFICATION_KINDS = ['ALERT', 'NOTICE'] as const satisfies readonly AppNotificationKind[];
export type InboxNotificationKind = (typeof INBOX_NOTIFICATION_KINDS)[number];

/** 알림함 목록 한 줄 */
export interface AppNotificationSummary {
  id: number;
  kind: AppNotificationKind;
  /** 대표 알림 종류 (공지사항은 없음) */
  alertCategory?: AlertCategory;
  /** 묶음에 든 알림 종류 전체 (중요한 순, 공지사항은 빈 목록) */
  alertCategories: AlertCategory[];
  title: string;
  /** 긴급 알림: 푸시 본문, 공지사항: 본문 앞부분 */
  body: string;
  leadArticleId?: number;
  /** 대표 기사 제목 (한국어 제목 우선) */
  leadArticleTitle?: string;
  /** 묶음 기사 수 (공지사항 0) */
  articleCount: number;
  isPinned: boolean;
  createdAt: Date;
  updatedAt: Date;
  /** 푸시를 보낸 시각 (방해 금지 시간으로 미뤄진 동안·푸시 없이 올린 공지는 없음) */
  pushedAt?: Date;
}

/** 알림 종류 하나에 든 기사 묶음 (상세 화면 구역 1개) */
export interface AppNotificationArticleSection {
  alertCategory: AlertCategory;
  articles: BriefingItem[];
}

/** 알린 기사 1건의 대응 안내 요약: 브리핑의 대응 + 대응 모아보기 구역별 건수 + 첫 공식 대응 (전체는 기사 상세) */
export interface AppNotificationArticleGuide {
  articleId: string;
  /** 원문에 적힌 대응 (AI 브리핑, 없으면 빈 배열) */
  actions: string[];
  officialCount: number;
  factCount: number;
  newsCount: number;
  /** 공식 대응 첫 항목 (KISA 먼저, 없으면 없음) */
  leadOfficialItem?: ResponseGuideItem;
}

/** 아침 요약의 분야 묶음 1개 (분야 code 는 카탈로그로 이름을 찾음, 분야 없는 소식은 other) */
export interface AppNotificationDigestFieldSection {
  fieldCode: string;
  articles: BriefingItem[];
}

/** 아침 요약 본문 (#93): 담긴 소식 수, 꼭 볼 것(주요), 분야별 묶음(건수 많은 순) */
export interface AppNotificationDigest {
  articleCount: number;
  notableArticles: BriefingItem[];
  fieldSections: AppNotificationDigestFieldSection[];
}

/** 알림함 상세: 목록 한 줄 + 전체 본문 + 링크(공지사항) + 종류별 기사 묶음·기사별 대응 안내(긴급 알림) + 아침 요약(DIGEST) */
export interface AppNotificationDetail extends AppNotificationSummary {
  linkUrl?: string;
  articleSections: AppNotificationArticleSection[];
  /** 알린 순서대로 앞 20건까지 (공지사항·이전 서버는 빈 배열) */
  articleGuides: AppNotificationArticleGuide[];
  /** DIGEST 만 있음 */
  digest?: AppNotificationDigest;
}

/** 알림함 목록 한 페이지 */
export interface AppNotificationPage {
  items: AppNotificationSummary[];
  nextCursor?: string;
}

/** 종류별 가장 최근 글 번호 (알림 종 점 표시용, 없으면 글이 없음) */
export interface LatestAppNotificationIds {
  latestAlertId?: number;
  latestNoticeId?: number;
}
