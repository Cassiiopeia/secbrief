import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { toMastheadDate } from '@/core/format/briefing-date-formatter';
import type { BriefingTopic } from '@/data/models/briefing-item';
import type { TrendTopic } from '@/data/models/trend-topic';
import {
  SbEntrance,
  SbInlineErrorState,
  SbInlineNotice,
  SbListRow,
  SbLoadingSkeleton,
  SbRatioRow,
  SbSectionHeader,
  SbStatTile,
  sbSpacing,
} from '@/design';

import { toTopicCoverKind } from '../articles/article-cover';
import { toTopicLabel } from '../articles/article-labels';
import { BROWSABLE_TOPICS, toBrowseHref, toTopicBrowseKey } from '../browse/browse-target';
import { toTrendShareRatio, toTrendTopicView } from '../trend/trend-view-state';
import type { useWeeklyTrend } from '../trend/use-weekly-trend';
import { type WeeklyTrendRanking, toWeeklyTrendRanking } from '../trend/weekly-trend-ranking';

const SECTION_TITLE = '주제별 주간 동향';
// 등장 순번: 1위 타일 0 > 주제 행 1~
const TOP_TILE_ORDER = 0;
// 0건 주제 이름을 한 줄에 이어 쓸 때의 구분 글자
const TOPIC_NAME_SEPARATOR = ' · ';

// 주제 누름 > 그 주제 모아 보기 (일반은 모아 보기가 없어 누름 없음)
function toTopicPressHandler(topic: BriefingTopic): (() => void) | undefined {
  if (!BROWSABLE_TOPICS.includes(topic)) return undefined;
  return () => router.push(toBrowseHref(toTopicBrowseKey(topic)));
}

/** 탐색 탭 주제별 주간 동향: 구역 제목(주 시작일) > 순위 (로딩 뼈대 / 오류·다시 시도 / 0건 안내 포함) */
export function ExploreTrendSection({ weeklyTrendQuery }: { weeklyTrendQuery: ReturnType<typeof useWeeklyTrend> }) {
  if (weeklyTrendQuery.isPending) {
    return (
      <View>
        <SbSectionHeader title={SECTION_TITLE} />
        <SbLoadingSkeleton variant="listRows" />
      </View>
    );
  }
  if (weeklyTrendQuery.isError) {
    const failure = toAppFailure(weeklyTrendQuery.error);
    return (
      <View>
        <SbSectionHeader title={SECTION_TITLE} />
        <SbInlineErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => weeklyTrendQuery.refetch()} />
      </View>
    );
  }
  const ranking = toWeeklyTrendRanking(weeklyTrendQuery.data.topics);
  return (
    <View>
      <SbSectionHeader title={SECTION_TITLE} caption={`${toMastheadDate(weeklyTrendQuery.data.weekStartDate)} 부터 모은 소식이에요.`} />
      {ranking.isEmpty ? (
        <SbInlineNotice message="이번 주 소식을 모으고 있어요. 소식이 쌓이면 주제별로 정리해 드릴게요." />
      ) : (
        <ExploreTrendRanking ranking={ranking} />
      )}
    </View>
  );
}

/** 순위: 1위 주제 타일 > 나머지 주제 막대 (1위 대비, 지난주 대비 증감) > 0건 주제 한 줄 > 그 밖의 소식(일반) 한 줄 */
function ExploreTrendRanking({ ranking }: { ranking: WeeklyTrendRanking }) {
  const { topTopic, otherTopics, quietTopics, generalTopic } = ranking;
  const quietTopicNames = quietTopics.map((topic) => toTopicLabel(topic.topic)).join(TOPIC_NAME_SEPARATOR);
  return (
    <View>
      {topTopic === undefined ? (
        <SbInlineNotice message="이번 주에는 주제를 정한 소식이 아직 없어요." />
      ) : (
        <ExploreTopTrendTile topTopic={topTopic} />
      )}
      {otherTopics.map((topic, topicIndex) => {
        const trendView = toTrendTopicView(topic);
        return (
          <SbEntrance key={trendView.topic} order={TOP_TILE_ORDER + 1 + topicIndex}>
            <SbRatioRow
              title={trendView.topicLabel}
              valueLabel={trendView.countLabel}
              change={trendView.change}
              ratio={toTrendShareRatio(topic.weeklyCount, topTopic?.weeklyCount ?? 0)}
              order={TOP_TILE_ORDER + 1 + topicIndex}
              accessibilityLabel={`${trendView.topicLabel}, ${trendView.detail}`}
              onPress={toTopicPressHandler(topic.topic)}
            />
          </SbEntrance>
        );
      })}
      {quietTopics.length > 0 && (
        <SbListRow title="이번 주 소식이 없는 주제" detail={quietTopicNames} accessibilityLabel={`이번 주 소식이 없는 주제, ${quietTopicNames}`} />
      )}
      {generalTopic !== undefined && (
        <SbListRow
          title="그 밖의 소식"
          detail={`${generalTopic.weeklyCount}건 · 주제를 따로 나누지 않은 소식이에요.`}
          accessibilityLabel={`그 밖의 소식, ${generalTopic.weeklyCount}건`}
        />
      )}
    </View>
  );
}

/** 이번 주 1위 주제 타일 (일반 제외: 홈의 1위 주제 타일과 같은 기준) */
function ExploreTopTrendTile({ topTopic }: { topTopic: TrendTopic }) {
  const topTrendView = toTrendTopicView(topTopic);
  return (
    <SbEntrance order={TOP_TILE_ORDER}>
      <View style={styles.topTile}>
        <SbStatTile
          label="이번 주 1위 주제"
          value={{ text: topTrendView.topicLabel }}
          detail={topTrendView.detail}
          tone="feature"
          backgroundCoverKind={toTopicCoverKind(topTopic.topic)}
          onPress={toTopicPressHandler(topTopic.topic)}
        />
      </View>
    </SbEntrance>
  );
}

const styles = StyleSheet.create({
  topTile: { flexDirection: 'row', paddingHorizontal: sbSpacing.tileHorizontal },
});
