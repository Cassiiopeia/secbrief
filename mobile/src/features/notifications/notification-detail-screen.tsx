import { router, useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';
import { ScrollView, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { AppNotificationKind } from '@/data/models/app-notification';
import { SbEmptyState, SbErrorState, SbLoadingSkeleton, SbScreen, SbScreenHeader, sbSpacing } from '@/design';

import { toNotificationInboxRoute } from '../navigation/app-routes';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { AlertDetailContent } from './alert-detail-content';
import { DigestDetailContent } from './digest-detail-content';
import { NoticeDetailContent } from './notice-detail-content';
import { toNotificationDetailHeaderTitle, toNotificationNotFoundView } from './notification-detail-texts';
import { toDetailRouteForReceivedKind } from './notification-target-route';
import { NOTIFICATION_NOT_FOUND_ERROR_CODE, useAppNotificationDetail } from './use-app-notification-detail';
import { useNotificationReadStateUpdaters } from './use-notification-read-state';

const BACK_ACCESSIBILITY_LABEL = '뒤로';

/** 긴급 알림 상세 라우트 화면 (app/notifications/[notificationId]) */
export function NotificationDetailScreen() {
  const { notificationId } = useLocalSearchParams<{ notificationId: string }>();
  return <AppNotificationDetailFrame notificationId={notificationId ?? ''} expectedKind="ALERT" />;
}

/** 공지사항 상세 라우트 화면 (app/notices/[noticeId]) */
export function NoticeDetailScreen() {
  const { noticeId } = useLocalSearchParams<{ noticeId: string }>();
  return <AppNotificationDetailFrame notificationId={noticeId ?? ''} expectedKind="NOTICE" />;
}

/**
 * 알림함 상세 틀 (탭 밖): 머리 > 상태(로딩 / 찾지 못함 / 오류) 또는 종류에 맞는 본문
 * 연 글로 기록 (기기에만 저장). 받은 글의 종류가 경로와 다르면 맞는 상세 경로로 바꿔 엶 (notices/{알림 번호} 등, #87)
 */
function AppNotificationDetailFrame({ notificationId, expectedKind }: { notificationId: string; expectedKind: AppNotificationKind }) {
  const detailQuery = useAppNotificationDetail(notificationId);
  const { markNotificationOpened } = useNotificationReadStateUpdaters();
  const kind = detailQuery.data?.kind ?? expectedKind;
  const redirectRoute = detailQuery.data && toDetailRouteForReceivedKind(expectedKind, detailQuery.data.kind, notificationId);

  useEffect(() => {
    if (/^\d+$/.test(notificationId)) markNotificationOpened(Number(notificationId));
  }, [notificationId, markNotificationOpened]);

  useEffect(() => {
    if (redirectRoute !== undefined) router.replace(redirectRoute);
  }, [redirectRoute]);

  return (
    <SbScreen>
      <SbScreenHeader title={toNotificationDetailHeaderTitle(kind)} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <ScrollView contentContainerStyle={styles.content}>{renderBody()}</ScrollView>
    </SbScreen>
  );

  function renderBody() {
    // 맞는 경로로 바꾸는 동안 다른 종류의 본문을 그리지 않음
    if (detailQuery.isPending || redirectRoute !== undefined) return <SbLoadingSkeleton variant="listRows" />;
    if (detailQuery.isError) {
      const failure = toAppFailure(detailQuery.error);
      if (failure.errorCode === NOTIFICATION_NOT_FOUND_ERROR_CODE) {
        const notFoundView = toNotificationNotFoundView(kind, failure.errorCode);
        return (
          <SbEmptyState
            title={notFoundView.title}
            description={notFoundView.description}
            illustration="notFound"
            actionLabel="알림함 보기"
            onActionPress={() => router.replace(toNotificationInboxRoute(notFoundView.inboxSegment))}
            errorCode={notFoundView.errorCode}
          />
        );
      }
      return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => detailQuery.refetch()} />;
    }
    if (kind === 'NOTICE') return <NoticeDetailContent detail={detailQuery.data} />;
    if (kind === 'DIGEST') return <DigestDetailContent detail={detailQuery.data} />;
    return <AlertDetailContent detail={detailQuery.data} />;
  }
}

const styles = StyleSheet.create({
  // 상태 안내가 남은 높이 가운데에 오도록, 마지막 행이 화면 아래 끝에 붙지 않게
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
});
