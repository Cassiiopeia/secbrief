/** 앱 화면 경로 단일 원본 (app/ 아래 파일 이름과 같아야 함) */
export const APP_ROUTES = {
  home: '/',
  news: '/news',
  vulnerability: '/vulnerability',
  explore: '/explore',
  search: '/search',
  notifications: '/notifications',
} as const;

// 탭 밖 상세 경로 (app/articles/[articleId], app/notifications/[notificationId], app/notices/[noticeId] 와 같아야 함)
export type ArticleDetailRoute = `/articles/${string}`;
// 기사 상세를 열며 내려갈 구역 (?focus= 값): responseGuide 대응 모아보기 (알림 상세에서 들어옴)
export const ARTICLE_DETAIL_FOCUSES = ['responseGuide'] as const;
export type ArticleDetailFocus = (typeof ARTICLE_DETAIL_FOCUSES)[number];
export type NotificationDetailRoute = `/notifications/${string}`;
export type NoticeDetailRoute = `/notices/${string}`;
// 알림함 공지사항 칸으로 바로 여는 경로 (segment 값은 AppNotificationKind)
export type NotificationInboxRoute = typeof APP_ROUTES.notifications | `${typeof APP_ROUTES.notifications}?segment=${string}`;

export const toArticleDetailRoute = (articleId: string, focus?: ArticleDetailFocus): ArticleDetailRoute =>
  focus === undefined ? `/articles/${encodeURIComponent(articleId)}` : `/articles/${encodeURIComponent(articleId)}?focus=${focus}`;
export const toNotificationDetailRoute = (notificationId: string): NotificationDetailRoute =>
  `/notifications/${encodeURIComponent(notificationId)}`;
export const toNoticeDetailRoute = (noticeId: string): NoticeDetailRoute => `/notices/${encodeURIComponent(noticeId)}`;
