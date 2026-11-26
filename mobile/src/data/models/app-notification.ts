import type { AlertCategory } from './alert-category';
import type { BriefingItem } from './briefing-item';
import type { ResponseGuideItem } from './response-guide';

/** 알림함 글 종류 2개: ALERT 긴급 알림 기록(서버가 발송 때 남김), NOTICE 공지사항(관리자가 씀) */
export const APP_NOTIFICATION_KINDS = ['ALERT', 'NOTICE'] as const;
export type AppNotificationKind = (typeof APP_NOTIFICATION_KINDS)[number];

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

/** 알림함 상세: 목록 한 줄 + 전체 본문 + 링크(공지사항) + 종류별 기사 묶음·기사별 대응 안내(긴급 알림) */
export interface AppNotificationDetail extends AppNotificationSummary {
  linkUrl?: string;
  articleSections: AppNotificationArticleSection[];
  /** 알린 순서대로 앞 20건까지 (공지사항·이전 서버는 빈 배열) */
  articleGuides: AppNotificationArticleGuide[];
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
