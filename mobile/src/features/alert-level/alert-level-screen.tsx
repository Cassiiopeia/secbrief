import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import {
  type SbColors,
  SbCapsLabel,
  SbEmptyState,
  SbEntrance,
  SbErrorState,
  SbLevelGauge,
  SbLoadingSkeleton,
  SbPressable,
  SbScreen,
  SbScreenHeader,
  SbSectionHeader,
  SbStatTile,
  SbText,
  SbTimeline,
  SbTimelineGroup,
  SbTimelineItem,
  createSbRefreshControl,
  sbRadius,
  sbSize,
  sbSpacing,
  toSbAlertLevelColor,
  useSbStyles,
  useSbTheme,
} from '@/design';

import { openOriginalArticle } from '../articles/open-original-article';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { toBrowseHref } from '../browse/browse-target';
import { goBackOrHome } from '../navigation/go-back-or-home';
import {
  type AlertLevelChangeItemView,
  type AlertLevelDashboardView,
  type AlertLevelHeroView,
  type InternationalIndicatorView,
  type WeeklyThreatTileView,
  toAlertLevelDashboardView,
} from './alert-level-dashboard-view';
import {
  ALERT_LEVEL_CAPTION,
  ALERT_LEVEL_SCREEN_TITLE,
  ALERT_LEVEL_SOURCE_LINK_LABEL,
  INTERNATIONAL_SECTION_CAPTION,
  INTERNATIONAL_SECTION_TITLE,
} from './alert-level-texts';
import { useAlertLevelDashboard } from './use-alert-level-dashboard';

const BACK_ACCESSIBILITY_LABEL = '뒤로';
// 등장 순번: 머리 > 이번 주 위협 > 해외 지표 > 변경 이력
const HERO_ORDER = 0;
const WEEKLY_ORDER = 1;
const INDICATORS_ORDER = 2;
const CHANGES_ORDER = 3;
// 변경 이력을 접었을 때 보이는 건수 (나머지는 더 보기)
const COLLAPSED_CHANGE_COUNT = 5;

/** 사이버위기 경보 화면 (탭 밖): 지금 단계(5단계 막대) > 이번 주 위협 숫자 > 해외 지표 > 변경 이력. 당겨서 새로고침 */
export function AlertLevelScreen() {
  const { colors } = useSbTheme();
  const dashboardQuery = useAlertLevelDashboard((dashboard) => toAlertLevelDashboardView(dashboard, new Date()));
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(dashboardQuery.refetch);
  return (
    <SbScreen>
      <SbScreenHeader title={ALERT_LEVEL_SCREEN_TITLE} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}>
        {renderBody()}
      </ScrollView>
    </SbScreen>
  );

  /** 상태별 본문: 로딩 뼈대 / 오류 / 경보 공지 없음 / 화면 */
  function renderBody() {
    if (dashboardQuery.isPending) return <SbLoadingSkeleton variant="briefCards" />;
    if (dashboardQuery.isError) {
      const failure = toAppFailure(dashboardQuery.error);
      return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => dashboardQuery.refetch()} />;
    }
    return <AlertLevelDashboardContent view={dashboardQuery.data} />;
  }
}

function AlertLevelDashboardContent({ view }: { view: AlertLevelDashboardView }) {
  return (
    <View>
      <SbEntrance order={HERO_ORDER}>
        {view.hero === undefined ? (
          <SbEmptyState title="아직 받은 경보 공지가 없어요" description="KISA 경보단계 공지가 올라오면 여기에 보여 드릴게요." />
        ) : (
          <AlertLevelHero hero={view.hero} />
        )}
      </SbEntrance>
      {view.weeklyTiles.length > 0 && (
        <SbEntrance order={WEEKLY_ORDER}>
          <SbSectionHeader title="이번 주 위협" />
          <WeeklyThreatTiles tiles={view.weeklyTiles} />
        </SbEntrance>
      )}
      {view.indicators.length > 0 && (
        <SbEntrance order={INDICATORS_ORDER}>
          <SbSectionHeader title={INTERNATIONAL_SECTION_TITLE} caption={INTERNATIONAL_SECTION_CAPTION} />
          <View style={styles.cards}>
            {view.indicators.map((indicator) => (
              <InternationalIndicatorCard key={indicator.key} indicator={indicator} />
            ))}
          </View>
        </SbEntrance>
      )}
      {view.changes.length > 0 && (
        <SbEntrance order={CHANGES_ORDER}>
          <AlertLevelChangeTimeline changes={view.changes} />
        </SbEntrance>
      )}
    </View>
  );
}

/** 머리 카드: 작은 라벨 > 단계 이름(단계 색) > 시작일 문장 > 한 줄 뜻 > 5단계 막대 > 원문 링크 */
function AlertLevelHero({ hero }: { hero: AlertLevelHeroView }) {
  const { colors } = useSbTheme();
  const themedStyles = useSbStyles(createThemedStyles);
  return (
    <View style={styles.cards}>
      <View style={themedStyles.card}>
        <SbCapsLabel text={ALERT_LEVEL_CAPTION} color={colors.tertiaryLabel} />
        {hero.levelName !== undefined && hero.tone !== undefined ? (
          <SbText variant="levelValue" color={toSbAlertLevelColor(colors, hero.tone)}>
            {hero.levelName}
          </SbText>
        ) : (
          <SbText variant="sectionTitle" color={colors.label}>
            {hero.rawTitle}
          </SbText>
        )}
        <SbText variant="sectionTitle" color={colors.label}>
          {hero.sinceLabel}
        </SbText>
        <SbText variant="body" color={colors.secondaryLabel}>
          {hero.meaning}
        </SbText>
        <View style={styles.gauge}>
          <SbLevelGauge segments={hero.gaugeSegments} currentStep={hero.currentStep} size="large" accessibilityLabel={hero.gaugeAccessibilityLabel} />
        </View>
        <SourceLink label={ALERT_LEVEL_SOURCE_LINK_LABEL} url={hero.sourceUrl} />
      </View>
    </View>
  );
}

/** 변경 이력 타임라인: 최근 5건 > 더 보기로 전부 (누르면 KISA 원문) */
function AlertLevelChangeTimeline({ changes }: { changes: AlertLevelChangeItemView[] }) {
  const [isExpanded, setIsExpanded] = useState(false);
  const hasHiddenChanges = changes.length > COLLAPSED_CHANGE_COUNT;
  const visibleChanges = isExpanded ? changes : changes.slice(0, COLLAPSED_CHANGE_COUNT);
  return (
    <View>
      <SbSectionHeader
        title="변경 이력"
        action={
          hasHiddenChanges
            ? {
                label: isExpanded ? '접기' : '더 보기',
                accessibilityLabel: isExpanded ? '변경 이력 접기' : `변경 이력 ${changes.length}건 모두 보기`,
                onPress: () => setIsExpanded(!isExpanded),
              }
            : undefined
        }
      />
      <View style={styles.timeline}>
        <SbTimeline>
          {visibleChanges.map((change) => (
            <SbTimelineGroup key={change.key} dateLabel={change.dateLabel}>
              <SbTimelineItem
                title={change.title}
                metaLabel={change.metaLabel}
                accessibilityLabel={change.accessibilityLabel}
                onPress={() => openOriginalArticle(change.originalUrl)}
              />
            </SbTimelineGroup>
          ))}
        </SbTimeline>
      </View>
    </View>
  );
}

/** 이번 주 숫자 3개: 누르면 그 목록 (악용 중 KEV, 긴급 소식, KISA 보안공지) */
function WeeklyThreatTiles({ tiles }: { tiles: WeeklyThreatTileView[] }) {
  return (
    <View style={styles.tileRow}>
      {tiles.map((tile) => (
        <SbStatTile
          key={tile.key}
          label={tile.label}
          value={{ text: tile.valueText }}
          detail={tile.detail}
          tone={tile.tone}
          onPress={() => router.push(toBrowseHref(tile.browseKey))}
        />
      ))}
    </View>
  );
}

/** 해외 지표 카드: 이름 + 단계 이름 > 지표 자체의 단계 막대 > 뜻 > 단계 위치·기준·확인 시각 > 출처 링크 */
function InternationalIndicatorCard({ indicator }: { indicator: InternationalIndicatorView }) {
  const { colors } = useSbTheme();
  const themedStyles = useSbStyles(createThemedStyles);
  return (
    <View style={themedStyles.card} accessible accessibilityLabel={indicator.accessibilityLabel}>
      <View style={styles.indicatorHeader}>
        <View style={styles.indicatorName}>
          <SbText variant="rowTitle" color={colors.label}>
            {indicator.name}
          </SbText>
        </View>
        {indicator.levelLabel !== undefined && (
          <SbText variant="sectionTitle" color={colors.label}>
            {indicator.levelLabel}
          </SbText>
        )}
      </View>
      <SbLevelGauge segments={indicator.gaugeSegments} currentStep={indicator.currentStep} size="compact" accessibilityLabel={indicator.accessibilityLabel} />
      <SbText variant="body" color={colors.secondaryLabel}>
        {indicator.meaning}
      </SbText>
      {indicator.captionLabel !== '' && (
        <SbText variant="caption" color={colors.tertiaryLabel}>
          {indicator.captionLabel}
        </SbText>
      )}
      <SourceLink label={`${indicator.sourceLinkLabel} 원문 보기`} url={indicator.sourceUrl} />
    </View>
  );
}

/** 원문 링크 한 줄: 글 + 오른쪽 화살표, 인앱 브라우저로 열기 */
function SourceLink({ label, url }: { label: string; url: string }) {
  const { colors } = useSbTheme();
  return (
    <SbPressable accessibilityLabel={label} accessibilityRole="link" feedback="dim" onPress={() => openOriginalArticle(url)}>
      <View style={styles.sourceLink}>
        <SbText variant="caption" color={colors.tertiaryLabel}>
          {label}
        </SbText>
        <MaterialIcons name="chevron-right" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
      </View>
    </SbPressable>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
  cards: { paddingHorizontal: sbSpacing.tileHorizontal, gap: sbSpacing.tileGap },
  tileRow: { flexDirection: 'row', gap: sbSpacing.tileGap, paddingHorizontal: sbSpacing.tileHorizontal },
  timeline: { paddingHorizontal: sbSpacing.pageHorizontal },
  gauge: { paddingVertical: sbSpacing.small },
  indicatorHeader: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
  indicatorName: { flex: 1 },
  sourceLink: { flexDirection: 'row', alignItems: 'center', minHeight: sbSize.minimumTouchTarget },
});

const createThemedStyles = (colors: SbColors) =>
  StyleSheet.create({
    card: { backgroundColor: colors.surface, borderRadius: sbRadius.tile, padding: sbSpacing.tilePadding, gap: sbSpacing.small },
  });
