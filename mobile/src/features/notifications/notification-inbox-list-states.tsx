import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { InboxNotificationKind } from '@/data/models/app-notification';
import { SbEmptyState, SbErrorState, SbLoadingSkeleton, type SbStateIllustrationKind, SbText, sbSpacing, useSbTheme } from '@/design';

import { APP_ROUTES } from '../navigation/app-routes';
import { toNotificationInboxEnableAlertPrompt } from './notification-inbox-view';
import { useAlertCategorySettings } from './use-alert-category-settings';

// 종류별 빈 상태 문구·그림, 목록 끝 안내 (긴급 알림 기록은 서버가 30일 보관)
const INBOX_TEXTS: Record<InboxNotificationKind, { emptyTitle: string; emptyDescription: string; emptyIllustration: SbStateIllustrationKind; endNote?: string }> = {
  ALERT: {
    emptyTitle: '아직 받은 알림이 없어요',
    emptyDescription: '긴급 보안 소식이 들어오면 여기에 모아 둘게요.',
    emptyIllustration: 'inboxEmpty',
    endNote: '최근 30일 동안 보낸 알림을 모아 둬요.',
  },
  NOTICE: {
    emptyTitle: '아직 올라온 공지사항이 없어요',
    emptyDescription: '서비스 소식이 생기면 여기에서 알려 드릴게요.',
    emptyIllustration: 'noticeEmpty',
  },
};
// 다음 페이지를 불러오는 동안 목록 끝에 보이는 뼈대 행 수
const NEXT_PAGE_SKELETON_ROW_COUNT = 2;

interface NotificationInboxListStateProps {
  kind: InboxNotificationKind;
  isLoading: boolean;
  error: unknown;
  onRetry: () => void;
}

/** 글이 없을 때: 로딩 뼈대 / 오류 / 빈 상태 (+ 알림이 꺼져 있으면 켜러 가기) */
export function NotificationInboxListState({ kind, isLoading, error, onRetry }: NotificationInboxListStateProps) {
  const alertCategorySettings = useAlertCategorySettings();
  if (isLoading) return <SbLoadingSkeleton variant="listRows" />;
  if (error !== null && error !== undefined) {
    const failure = toAppFailure(error);
    return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={onRetry} />;
  }
  // 알림이 모두 꺼진 기기면 켜러 가는 버튼 (설정 탭)
  const enableAlertPrompt = toNotificationInboxEnableAlertPrompt(
    kind,
    alertCategorySettings.enabledAlertCategories.length > 0,
    alertCategorySettings.unavailableReason === undefined,
  );
  const texts = INBOX_TEXTS[kind];
  return (
    <SbEmptyState
      title={texts.emptyTitle}
      description={enableAlertPrompt?.description ?? texts.emptyDescription}
      illustration={texts.emptyIllustration}
      actionLabel={enableAlertPrompt?.actionLabel}
      onActionPress={() => router.navigate(APP_ROUTES.settings)}
    />
  );
}

/** 목록 끝: 다음 페이지 뼈대 / 마지막이면 보관 기간 안내 (없으면 그리지 않음) */
export function NotificationInboxListFooter({ kind, isFetchingNextPage, isAtEnd }: { kind: InboxNotificationKind; isFetchingNextPage: boolean; isAtEnd: boolean }) {
  const { colors } = useSbTheme();
  const endNote = INBOX_TEXTS[kind].endNote;
  if (isFetchingNextPage) return <SbLoadingSkeleton variant="listRows" rowCount={NEXT_PAGE_SKELETON_ROW_COUNT} />;
  if (!isAtEnd || endNote === undefined) return null;
  return (
    <View style={styles.endNote}>
      <SbText variant="caption" color={colors.tertiaryLabel} isCentered>
        {endNote}
      </SbText>
    </View>
  );
}

const styles = StyleSheet.create({
  endNote: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.large },
});
