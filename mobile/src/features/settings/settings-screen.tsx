import { Linking, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { appInfo } from '@/core/config/app-info';
import { supportLinks, toContactMailUrl } from '@/core/config/support-links';
import { toAppFailure } from '@/core/error/app-failure';
import { SbCapsLabel, SbErrorState, SbListRow, SbLoadingSkeleton, SbMasthead, SbScreen, sbSpacing, useSbTheme } from '@/design';

import { openOriginalArticle } from '../articles/open-original-article';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { toSourceStatusRowView } from './settings-view-state';
import { useCollectSourceStatuses } from './use-collect-source-statuses';

const MASTHEAD_TITLE_LINES = ['설정'];
const OPEN_ICON_NAME = 'open-in-new';

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

/** 설정 탭: 문의 > 약관 > 출처(마지막 갱신) > 앱 정보. 출처 조회 실패는 그 구역에만 */
export function SettingsScreen() {
  const { colors } = useSbTheme();
  const sourceStatusesQuery = useCollectSourceStatuses();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(sourceStatusesQuery.refetch);

  return (
    <SbScreen>
      <ScrollView
        refreshControl={<RefreshControl refreshing={isPullRefreshing} onRefresh={onPullRefresh} tintColor={colors.label} />}>
        <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />

        {renderSectionLabel('문의')}
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

        {renderSectionLabel('약관')}
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

        {renderSectionLabel('출처')}
        {renderSourceStatuses()}

        {renderSectionLabel('앱 정보')}
        <SbListRow title="버전" detail={appInfo.version} accessibilityLabel={`앱 버전 ${appInfo.version}`} />
        <SbListRow
          title="해외 소식 제목은 AI 가 번역해요"
          detail="원문 제목을 함께 보여 드려요. 정확한 내용은 원문에서 확인해 주세요."
          accessibilityLabel="해외 소식 제목은 AI 가 번역해요. 원문 제목을 함께 보여 드려요."
        />
      </ScrollView>
    </SbScreen>
  );

  function renderSectionLabel(text: string) {
    return (
      <View style={styles.sectionLabel}>
        <SbCapsLabel text={text} color={colors.tertiaryLabel} />
      </View>
    );
  }

  /** 출처 구역: 로딩 / 오류 / 출처 목록 (누름 없음) */
  function renderSourceStatuses() {
    if (sourceStatusesQuery.isPending) return <SbLoadingSkeleton />;
    if (sourceStatusesQuery.isError) {
      const failure = toAppFailure(sourceStatusesQuery.error);
      return (
        <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => sourceStatusesQuery.refetch()} />
      );
    }
    const now = new Date();
    return sourceStatusesQuery.data.map((status) => {
      const rowView = toSourceStatusRowView(status, now);
      return (
        <SbListRow key={rowView.code} title={rowView.title} detail={rowView.detail} accessibilityLabel={`${rowView.title}, ${rowView.detail}`} />
      );
    });
  }
}

const styles = StyleSheet.create({
  sectionLabel: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.sectionTop, paddingBottom: sbSpacing.small },
});
