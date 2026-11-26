import { router, useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';
import { ScrollView, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { AppNotificationKind } from '@/data/models/app-notification';
import { SbEmptyState, SbErrorState, SbLoadingSkeleton, SbScreen, SbScreenHeader, sbSpacing } from '@/design';

import { APP_ROUTES } from '../navigation/app-routes';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { AlertDetailContent } from './alert-detail-content';
import { NoticeDetailContent } from './notice-detail-content';
import { NOTIFICATION_NOT_FOUND_ERROR_CODE, useAppNotificationDetail } from './use-app-notification-detail';
import { useNotificationReadStateUpdaters } from './use-notification-read-state';

const BACK_ACCESSIBILITY_LABEL = '뒤로';
// 종류별 머리 제목, 찾지 못했을 때 안내
const DETAIL_TEXTS: Record<AppNotificationKind, { headerTitle: string; notFoundTitle: string; notFoundDescription: string }> = {
  ALERT: { headerTitle: '알림', notFoundTitle: '보관 기간이 지난 알림이에요', notFoundDescription: '알림은 30일 동안 보관해요.' },
  NOTICE: { headerTitle: '공지사항', notFoundTitle: '내려간 공지사항이에요', notFoundDescription: '알림함에서 다른 공지사항을 볼 수 있어요.' },
};

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
 * 연 글로 기록 (기기에만 저장). 받은 글의 종류가 경로와 달라도 받은 종류로 그림
 */
function AppNotificationDetailFrame({ notificationId, expectedKind }: { notificationId: string; expectedKind: AppNotificationKind }) {
  const detailQuery = useAppNotificationDetail(notificationId);
  const { markNotificationOpened } = useNotificationReadStateUpdaters();
  const kind = detailQuery.data?.kind ?? expectedKind;

  useEffect(() => {
    if (/^\d+$/.test(notificationId)) markNotificationOpened(Number(notificationId));
  }, [notificationId, markNotificationOpened]);

  return (
    <SbScreen>
      <SbScreenHeader title={DETAIL_TEXTS[kind].headerTitle} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <ScrollView contentContainerStyle={styles.content}>{renderBody()}</ScrollView>
    </SbScreen>
  );

  function renderBody() {
    if (detailQuery.isPending) return <SbLoadingSkeleton variant="listRows" />;
    if (detailQuery.isError) {
      const failure = toAppFailure(detailQuery.error);
      if (failure.errorCode === NOTIFICATION_NOT_FOUND_ERROR_CODE) {
        return (
          <SbEmptyState
            title={DETAIL_TEXTS[kind].notFoundTitle}
            description={DETAIL_TEXTS[kind].notFoundDescription}
            illustration="notFound"
            actionLabel="알림함 보기"
            onActionPress={() => router.replace(APP_ROUTES.notifications)}
          />
        );
      }
      return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => detailQuery.refetch()} />;
    }
    return kind === 'NOTICE' ? <NoticeDetailContent detail={detailQuery.data} /> : <AlertDetailContent detail={detailQuery.data} />;
  }
}

const styles = StyleSheet.create({
  // 상태 안내가 남은 높이 가운데에 오도록, 마지막 행이 화면 아래 끝에 붙지 않게
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
});
