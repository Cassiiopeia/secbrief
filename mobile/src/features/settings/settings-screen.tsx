import { router } from 'expo-router';
import { Linking, ScrollView, StyleSheet } from 'react-native';

import { appInfo } from '@/core/config/app-info';
import { supportLinks, toContactMailUrl } from '@/core/config/support-links';
import { toAppFailure } from '@/core/error/app-failure';
import {
  SbEntrance,
  SbInlineErrorState,
  SbListRow,
  SbLoadingSkeleton,
  SbMasthead,
  SbScreen,
  SbSectionHeader,
  createSbRefreshControl,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { openOriginalArticle } from '../articles/open-original-article';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { APP_ROUTES } from '../navigation/app-routes';
import { AlertCategorySettingsSection } from '../notifications/alert-category-settings-section';
import { QuietHoursSettingsSection } from '../notifications/quiet-hours-settings-section';
import { useTabMastheadActions } from '../notifications/use-tab-masthead-actions';
import { toSourceStatusRowView } from './settings-view-state';
import { useCollectSourceStatuses } from './use-collect-source-statuses';

const MASTHEAD_TITLE_LINES = ['더보기'];
const OPEN_ICON_NAME = 'open-in-new';
// 출처 구역 로딩 뼈대 행 수
const SOURCE_SKELETON_ROW_COUNT = 4;

/** 메일로 문의: 메일 앱이 없으면 지원 페이지 (주소는 행에 늘 보임) */
async function openContactMail() {
  const contactMailUrl = toContactMailUrl(appInfo.version);
  const canOpenMail = await Linking.canOpenURL(contactMailUrl).catch(() => false);
  if (canOpenMail) {
    await Linking.openURL(contactMailUrl);
    return;
  }
  await openOriginalArticle(supportLinks.supportPageUrl);
}

/** 더보기 탭: 알림(알림함, 종류, 방해 금지 시간) > 문의 > 약관 > 출처(마지막 갱신) > 앱 정보. 출처 조회 실패는 그 구역에만. 당겨서 출처 새로고침 */
export function SettingsScreen() {
  const mastheadActions = useTabMastheadActions();
  const { colors } = useSbTheme();
  const sourceStatusesQuery = useCollectSourceStatuses();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(sourceStatusesQuery.refetch);

  return (
    <SbScreen>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}>
        <SbMasthead titleLines={MASTHEAD_TITLE_LINES} actions={mastheadActions} />
        <SbSectionHeader title="알림" />
        <SbListRow
          title="알림함"
          detail="받은 알림과 공지사항을 모아 봐요"
          trailingIconName="chevron-right"
          accessibilityLabel="알림함 열기, 받은 알림과 공지사항을 모아 봐요"
          onPress={() => router.push(APP_ROUTES.notifications)}
        />
        <AlertCategorySettingsSection />
        <QuietHoursSettingsSection />
        <SettingsContactSection />
        <SettingsTermsSection />
        <SbSectionHeader title="출처" />
        <SettingsSourceStatusList sourceStatusesQuery={sourceStatusesQuery} />
        <SettingsAppInfoSection />
      </ScrollView>
    </SbScreen>
  );
}

/** 문의 구역: 메일로 문의, 지원 페이지 */
function SettingsContactSection() {
  return (
    <>
      <SbSectionHeader title="문의" />
      <SbListRow
        title="메일로 문의하기"
        detail={supportLinks.contactEmail}
        trailingIconName="mail-outline"
        accessibilityLabel={`메일로 문의하기, ${supportLinks.contactEmail}`}
        onPress={openContactMail}
      />
      <SbListRow
        title="지원 페이지"
        trailingIconName={OPEN_ICON_NAME}
        accessibilityLabel="지원 페이지 열기"
        onPress={() => openOriginalArticle(supportLinks.supportPageUrl)}
      />
    </>
  );
}

/** 약관 구역: 개인정보처리방침, 이용약관 */
function SettingsTermsSection() {
  return (
    <>
      <SbSectionHeader title="약관" />
      <SbListRow
        title="개인정보처리방침"
        trailingIconName={OPEN_ICON_NAME}
        accessibilityLabel="개인정보처리방침 열기"
        onPress={() => openOriginalArticle(supportLinks.privacyPolicyUrl)}
      />
      <SbListRow
        title="이용약관"
        trailingIconName={OPEN_ICON_NAME}
        accessibilityLabel="이용약관 열기"
        onPress={() => openOriginalArticle(supportLinks.termsUrl)}
      />
    </>
  );
}

/** 출처 목록: 로딩 / 오류 / 출처별 마지막 갱신 (누름 없음) */
function SettingsSourceStatusList({ sourceStatusesQuery }: { sourceStatusesQuery: ReturnType<typeof useCollectSourceStatuses> }) {
  if (sourceStatusesQuery.isPending) return <SbLoadingSkeleton variant="listRows" rowCount={SOURCE_SKELETON_ROW_COUNT} />;
  if (sourceStatusesQuery.isError) {
    const failure = toAppFailure(sourceStatusesQuery.error);
    return <SbInlineErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => sourceStatusesQuery.refetch()} />;
  }
  const now = new Date();
  const rowViews = sourceStatusesQuery.data.map((status) => toSourceStatusRowView(status, now));
  return (
    <SbEntrance order={0}>
      {rowViews.map((rowView) => (
        <SbListRow key={rowView.code} title={rowView.title} detail={rowView.detail} accessibilityLabel={`${rowView.title}, ${rowView.detail}`} />
      ))}
    </SbEntrance>
  );
}

/** 앱 정보 구역: 버전, AI 번역 안내 */
function SettingsAppInfoSection() {
  return (
    <>
      <SbSectionHeader title="앱 정보" />
      <SbListRow title="버전" detail={appInfo.version} accessibilityLabel={`앱 버전 ${appInfo.version}`} />
      <SbListRow
        title="해외 소식 제목은 AI 가 번역해요"
        detail="원문 제목을 함께 보여 드려요. 정확한 내용은 원문에서 확인해 주세요."
        accessibilityLabel="해외 소식 제목은 AI 가 번역해요. 원문 제목을 함께 보여 드려요."
      />
    </>
  );
}

const styles = StyleSheet.create({
  content: { paddingBottom: sbSpacing.extraLarge },
});
