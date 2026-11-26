import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import {
  SbEntrance,
  SbMasthead,
  SbSectionHeader,
  SbShortcutGrid,
  SbStatTile,
  SbStatusBand,
  SbStreamRail,
  SbStreamRailItem,
  SbUrgentTile,
  sbSpacing,
  toSbAlertLevelColor,
  useSbTheme,
} from '@/design';

import { useArticleOpener } from '../articles/use-article-opener';
import { useReadArticleIds } from '../articles/use-read-articles';
import { toBrowseHref } from '../browse/browse-target';
import { TrendingKeywordStrip } from '../keywords/trending-keyword-strip';
import { APP_ROUTES } from '../navigation/app-routes';
import { toNewsHref } from '../news/news-feed-filter';
import { useTabMastheadActions } from '../notifications/use-tab-masthead-actions';
import type { AlertLevelView } from './alert-level-view';
import { TodayHeadlineSection } from './today-headline-section';
import { TodayNewArticlesStrip } from './today-new-articles-strip';
import type { TodayStreamItemView, TodayUrgentView, TodayViewState } from './today-view-state';

interface TodayBriefingContentProps {
  mastheadTitleLines: string[];
  state: TodayViewState;
  /** 사이버위기 경보 띠 (없으면 생략) */
  alertLevel?: AlertLevelView;
}

// 등장 순번: 머리 0 > 경보 띠 1 > 주요 소식 2 > 긴급 3 > 숫자 4 > 주제 5 > 스트림 제목 6 > 스트림 항목 7~
const MASTHEAD_ORDER = 0;
const ALERT_LEVEL_ORDER = 1;
const HEADLINE_ORDER = 2;
const URGENT_ORDER = 3;
const STAT_ORDER = 4;
const TOPIC_ORDER = 5;
const STREAM_ORDER = 6;

const onTrendPressed = () => router.navigate(APP_ROUTES.explore);
// 건수 타일·최신 소식 전체 보기 > 소식 탭의 전체
const onAllNewsPressed = () => router.navigate(toNewsHref('all'));
const onShortcutPressed = (browseKey: string) => router.push(toBrowseHref(browseKey));

/**
 * 오늘 브리핑 배치 (순서대로 등장): 머리 > (KISA 경보단계 띠) > (주요 소식 레일) > (긴급 타일) > 숫자 타일 > 주제·키워드 바로가기 > 최신 스트림
 * 소식을 누르면 앱 안 상세 화면
 */
export function TodayBriefingContent({ mastheadTitleLines, state, alertLevel }: TodayBriefingContentProps) {
  const mastheadActions = useTabMastheadActions();
  return (
    <View>
      <SbEntrance order={MASTHEAD_ORDER}>
        <SbMasthead titleLines={mastheadTitleLines} trailingLines={state.mastheadTrailingLines} actions={mastheadActions} />
      </SbEntrance>
      {alertLevel !== undefined && <TodayAlertLevelBand alertLevel={alertLevel} />}
      {state.headline !== undefined && (
        <SbEntrance order={HEADLINE_ORDER}>
          <TodayHeadlineSection headline={state.headline} />
        </SbEntrance>
      )}
      {state.urgent !== undefined && <TodayUrgentSection urgent={state.urgent} />}
      <TodayStatSection state={state} />
      <TodayNewArticlesStrip entranceOrder={STAT_ORDER} />
      <SbEntrance order={TOPIC_ORDER}>
        <SbSectionHeader title="주제별로 보기" />
        <SbShortcutGrid shortcuts={state.topicShortcuts} onShortcutPress={onShortcutPressed} />
        <TrendingKeywordStrip />
      </SbEntrance>
      {state.streamItems.length > 0 && <TodayStreamSection streamItems={state.streamItems} />}
    </View>
  );
}

/** 사이버위기 경보 띠: 지금 단계·시작일, 누르면 경보단계 화면 */
function TodayAlertLevelBand({ alertLevel }: { alertLevel: AlertLevelView }) {
  const { colors } = useSbTheme();
  return (
    <SbEntrance order={ALERT_LEVEL_ORDER}>
      <SbStatusBand
        iconName="shield"
        iconColor={alertLevel.tone === undefined ? undefined : toSbAlertLevelColor(colors, alertLevel.tone)}
        capsLabel={alertLevel.capsLabel}
        title={alertLevel.title}
        accessibilityLabel={alertLevel.accessibilityLabel}
        onPress={() => router.push(APP_ROUTES.alertLevel)}
      />
    </SbEntrance>
  );
}

/** 긴급 소식 구역: 구역 제목 > 긴급 타일 1건 */
function TodayUrgentSection({ urgent }: { urgent: TodayUrgentView }) {
  const openArticle = useArticleOpener();
  return (
    <SbEntrance order={URGENT_ORDER}>
      <SbSectionHeader title="긴급 소식" />
      <View style={styles.tiles}>
        <SbUrgentTile
          capsLabel={urgent.capsLabel}
          title={urgent.title}
          footerLabel={urgent.footerLabel}
          translatedMarkLabel={urgent.translatedMarkLabel}
          coverImageUrl={urgent.coverImageUrl}
          onPress={() => openArticle(urgent.article)}
        />
      </View>
    </SbEntrance>
  );
}

/** 한눈에 보기 구역: 오늘·이번 주 건수 타일(누르면 소식 탭 전체) > (이번 주 1위 주제 타일: 누르면 탐색 탭) */
function TodayStatSection({ state }: { state: TodayViewState }) {
  const { trend } = state;
  return (
    <SbEntrance order={STAT_ORDER}>
      <SbSectionHeader title="한눈에 보기" />
      <View style={styles.tiles}>
        <View style={styles.statRow}>
          <SbStatTile
            label="오늘"
            value={{ countTarget: state.todayCount, countUnit: '건' }}
            detail="오늘 올라온 소식"
            tone="neutral"
            onPress={onAllNewsPressed}
          />
          <SbStatTile
            label="이번 주"
            value={{ countTarget: state.weeklyCount, countUnit: '건' }}
            detail={state.weeklyDetail}
            tone="neutral"
            onPress={onAllNewsPressed}
          />
        </View>
        {trend !== undefined && (
          <View style={styles.statRow}>
            <SbStatTile
              label="이번 주 1위 주제"
              value={{ text: trend.topicName }}
              detail={trend.detail}
              tone="feature"
              backgroundCoverKind={trend.coverKind}
              onPress={onTrendPressed}
            />
          </View>
        )}
      </View>
    </SbEntrance>
  );
}

/** 최신 소식 구역: 구역 제목(전체 보기) > 스트림 레일 (맨 위 항목은 레일 점이 맥박침) */
function TodayStreamSection({ streamItems }: { streamItems: TodayStreamItemView[] }) {
  const openArticle = useArticleOpener();
  const readArticleIds = useReadArticleIds();
  return (
    <View style={styles.streamSection}>
      <SbEntrance order={STREAM_ORDER}>
        <SbSectionHeader
          title="최신 소식"
          action={{ label: '전체 보기', accessibilityLabel: '소식 전체 보기', onPress: onAllNewsPressed }}
        />
      </SbEntrance>
      <View style={styles.stream}>
        <SbStreamRail>
          {streamItems.map((item, itemIndex) => (
            <SbEntrance key={item.id} order={STREAM_ORDER + 1 + itemIndex}>
              <SbStreamRailItem
                timeLabel={item.timeLabel}
                title={item.title}
                translatedMarkLabel={item.translatedMarkLabel}
                thumbnailImageUrl={item.thumbnailImageUrl}
                isLatest={itemIndex === 0}
                isRead={readArticleIds.has(item.id)}
                onPress={() => openArticle(item.article)}
              />
            </SbEntrance>
          ))}
        </SbStreamRail>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  tiles: { paddingHorizontal: sbSpacing.tileHorizontal, gap: sbSpacing.tileGap },
  statRow: { flexDirection: 'row', gap: sbSpacing.tileGap },
  streamSection: { paddingBottom: sbSpacing.large },
  stream: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small },
});
