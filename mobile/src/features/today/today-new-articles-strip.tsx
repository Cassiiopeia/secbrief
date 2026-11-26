import { router } from "expo-router";

import { StyleSheet, View } from "react-native";

import {
  SbCountTile,
  SbEntrance,
  SbHorizontalRail,
  SbSectionHeader,
  SbText,
  sbSpacing,
  useSbTheme,
} from "@/design";

import { useNewArticleCounts } from "../catalog/use-new-article-counts";
import { useTagCatalogIndex } from "../catalog/use-tag-catalog";
import { toTodayNewArticlesView } from "./today-new-articles-view";

/**
 * 오늘 새로 들어온 소식 띠 (#93 시안 F 띠형 + 격자형의 대표 이름): 머리(마지막 수집 시각) > 옆으로 넘기는 건수 타일 > 0건 안내 한 줄
 * 건수를 못 받으면 구역을 숨김 (홈 나머지는 그대로)
 */
export function TodayNewArticlesStrip({
  entranceOrder,
}: {
  entranceOrder: number;
}) {
  const countsQuery = useNewArticleCounts();
  const catalogIndex = useTagCatalogIndex();
  const { colors } = useSbTheme();
  if (countsQuery.data === undefined) return null;
  const view = toTodayNewArticlesView(countsQuery.data, catalogIndex);
  return (
    <SbEntrance order={entranceOrder}>
      <SbSectionHeader title="오늘 새로 들어온 소식" caption={view.caption} />
      {view.tiles.length > 0 && (
        <SbHorizontalRail>
          {view.tiles.map((tile) => (
            <SbCountTile
              key={tile.key}
              valueLabel={tile.valueLabel}
              title={tile.title}
              detail={tile.detail}
              tone={tile.tone}
              accessibilityLabel={`${tile.accessibilityLabel}, 보기`}
              onPress={() => router.navigate(tile.href)}
            />
          ))}
        </SbHorizontalRail>
      )}
      {view.emptyNote !== undefined && (
        <View style={styles.emptyNote}>
          <SbText variant="caption" color={colors.tertiaryLabel}>
            {view.emptyNote}
          </SbText>
        </View>
      )}
    </SbEntrance>
  );
}

const styles = StyleSheet.create({
  emptyNote: {
    paddingHorizontal: sbSpacing.tileHorizontal,
    paddingTop: sbSpacing.small,
  },
});
