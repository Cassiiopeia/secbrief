import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { SbInlineErrorState, SbSectionHeader, SbTimeline, SbTimelineGroup, SbTimelineItem, sbSpacing } from '@/design';

import { useArticleOpener } from '../articles/use-article-opener';
import { useReadArticleIds } from '../articles/use-read-articles';
import { toBrowseHref, toTopicBrowseKey } from '../browse/browse-target';
import { toEventTimelineGroups } from './event-timeline';
import type { useEventArticles } from './use-event-articles';

const SECTION_TITLE = '행사 소식';
// 타임라인에 보일 행사 소식 수 (더 보려면 전체 보기)
const TIMELINE_EVENT_COUNT = 8;

const onSeeAllPressed = () => router.push(toBrowseHref(toTopicBrowseKey('event')));

/** 탐색 탭 행사 소식: 날짜별 타임라인 (게시일 기준). 조회 실패는 오류·다시 시도, 행사 소식이 없으면 구역을 그리지 않음 */
export function ExploreEventSection({ eventArticlesQuery }: { eventArticlesQuery: ReturnType<typeof useEventArticles> }) {
  const openArticle = useArticleOpener();
  const readArticleIds = useReadArticleIds();
  if (eventArticlesQuery.isError && eventArticlesQuery.data === undefined) {
    const failure = toAppFailure(eventArticlesQuery.error);
    return (
      <View>
        <SbSectionHeader title={SECTION_TITLE} />
        <SbInlineErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => eventArticlesQuery.refetch()} />
      </View>
    );
  }
  const timelineGroups = toEventTimelineGroups(eventArticlesQuery.data ?? [], new Date(), TIMELINE_EVENT_COUNT);
  if (timelineGroups.length === 0) return null;
  return (
    <View>
      <SbSectionHeader
        title={SECTION_TITLE}
        caption="컨퍼런스·세미나·교육 소식을 올라온 날짜순으로 보여 드려요."
        action={{ label: '전체 보기', accessibilityLabel: '행사 소식 전체 보기', onPress: onSeeAllPressed }}
      />
      <View style={styles.timeline}>
        <SbTimeline>
          {timelineGroups.map((timelineGroup) => (
            <SbTimelineGroup key={timelineGroup.dateKey} dateLabel={timelineGroup.dateLabel}>
              {timelineGroup.articles.map((article) => (
                <SbTimelineItem
                  key={article.id}
                  title={article.title}
                  metaLabel={article.compactMetaLabel}
                  isRead={readArticleIds.has(article.id)}
                  accessibilityLabel={article.accessibilityLabel}
                  onPress={() => openArticle(article)}
                />
              ))}
            </SbTimelineGroup>
          ))}
        </SbTimeline>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  timeline: { paddingTop: sbSpacing.small },
});
