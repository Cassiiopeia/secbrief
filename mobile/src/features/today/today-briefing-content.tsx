import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import {
  SbCapsLabel,
  SbEntrance,
  SbMasthead,
  SbStatTile,
  SbStreamRail,
  SbStreamRailItem,
  SbUrgentTile,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { openOriginalArticle } from '../articles/open-original-article';
import type { TodayViewState } from './today-view-state';

interface TodayBriefingContentProps {
  mastheadTitleLines: string[];
  state: TodayViewState;
}

// 등장 순번: 머리 0 > 긴급 1 > 통계 2 > 스트림 제목 3 > 스트림 항목 4~
const MASTHEAD_ORDER = 0;
const URGENT_ORDER = 1;
const STAT_ORDER = 2;
const STREAM_ORDER = 3;

// 동향 1위 누름 > 동향 탭
const onTrendPressed = () => router.navigate('/trend');

/**
 * 오늘 브리핑 배치: 머리 > (긴급 타일) > 통계 타일 2개 > 스트림. 순서대로 등장
 * 긴급 소식이 있으면 커버 배치, 없으면 통계 타일부터 시작하는 썸네일 배치
 */
export function TodayBriefingContent({ mastheadTitleLines, state }: TodayBriefingContentProps) {
  const { colors } = useSbTheme();
  const { urgent, trend } = state;
  const hasUrgent = urgent !== undefined;
  return (
    <View>
      <SbEntrance order={MASTHEAD_ORDER}>
        <SbMasthead titleLines={mastheadTitleLines} trailingLines={state.mastheadTrailingLines} />
      </SbEntrance>
      {urgent !== undefined && (
        <SbEntrance order={URGENT_ORDER}>
          <View style={styles.urgent}>
            <SbUrgentTile
              capsLabel={urgent.capsLabel}
              title={urgent.title}
              footerLabel={urgent.footerLabel}
              coverKind={urgent.coverKind}
              onPress={() => openOriginalArticle(urgent.originalUrl)}
            />
          </View>
        </SbEntrance>
      )}
      <SbEntrance order={STAT_ORDER}>
        <View style={styles.stats}>
          {trend !== undefined && (
            <SbStatTile
              label="이번 주 1위"
              value={{ text: trend.topicName }}
              detail={trend.detail}
              tone="feature"
              // 배경 그림은 커버 배치에서만 (썸네일 배치는 가볍게)
              backgroundCoverKind={hasUrgent ? 'lock' : undefined}
              onPress={onTrendPressed}
            />
          )}
          <SbStatTile
            label="이번 주"
            value={{ countTarget: state.weeklyCount, countUnit: '건' }}
            detail={state.weeklyDetail}
            tone="neutral"
          />
        </View>
      </SbEntrance>
      {state.streamItems.length > 0 && (
        <View style={styles.stream}>
          <SbEntrance order={STREAM_ORDER}>
            <SbCapsLabel text="스트림" color={colors.tertiaryLabel} />
          </SbEntrance>
          <SbStreamRail>
            {state.streamItems.map((item, itemIndex) => (
              <SbEntrance key={item.id} order={STREAM_ORDER + 1 + itemIndex}>
                <SbStreamRailItem
                  timeLabel={item.timeLabel}
                  title={item.title}
                  thumbnailKind={item.thumbnailKind}
                  thumbnailTone={item.thumbnailTone}
                  isLatest={itemIndex === 0}
                  onPress={() => openOriginalArticle(item.originalUrl)}
                />
              </SbEntrance>
            ))}
          </SbStreamRail>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  urgent: { paddingHorizontal: sbSpacing.tileHorizontal, marginBottom: sbSpacing.tileGap },
  stats: { flexDirection: 'row', gap: sbSpacing.tileGap, paddingHorizontal: sbSpacing.tileHorizontal },
  stream: {
    paddingHorizontal: sbSpacing.pageHorizontal,
    paddingTop: sbSpacing.sectionTop,
    paddingBottom: sbSpacing.large,
    gap: sbSpacing.compact,
  },
});
