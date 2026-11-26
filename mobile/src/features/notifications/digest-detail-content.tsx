import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import type { AppNotificationDetail } from '@/data/models/app-notification';
import { SbDisclosureRow, SbEntrance, SbReveal, SbSectionHeader, SbStatTile, SbText, sbSpacing, useSbTheme } from '@/design';

import { ArticleFeedItem } from '../articles/article-feed-item';
import { useTagCatalogIndex } from '../catalog/use-tag-catalog';
import { toDigestDetailView } from './digest-detail-view';

// 등장 순번: 머리 > 꼭 볼 것 > 분야별
const HEADING_ORDER = 0;
const NOTABLE_ORDER = 1;
const FIELD_ORDER = 2;

/**
 * 아침 요약 상세 (#93 시안 C③): 제목 > 타일 2개(담긴 소식, 분야 수) > 꼭 볼 것(주요 카드) > 분야별 묶음 (눌러서 펼침, 한 줄 행)
 * 분야 이름은 서버 카탈로그 그대로
 */
export function DigestDetailContent({ detail }: { detail: AppNotificationDetail }) {
  const { colors } = useSbTheme();
  const catalogIndex = useTagCatalogIndex();
  const detailView = toDigestDetailView(detail, catalogIndex, new Date());
  const [expandedFieldCodes, setExpandedFieldCodes] = useState<string[]>([]);
  const toggleField = (fieldCode: string) =>
    setExpandedFieldCodes((current) => (current.includes(fieldCode) ? current.filter((code) => code !== fieldCode) : [...current, fieldCode]));

  return (
    <>
      <SbEntrance order={HEADING_ORDER}>
        <View style={styles.heading} accessibilityRole="header">
          <SbText variant="detailTitle" color={colors.label}>
            {detail.title}
          </SbText>
        </View>
        <View style={styles.tiles}>
          <SbStatTile label="관심 소식" value={{ text: detailView.articleCountLabel }} detail="이번 요약에 담겼어요" tone="feature" />
          <SbStatTile label="분야" value={{ text: detailView.fieldCountLabel }} detail="분야별로 묶었어요" tone="neutral" />
        </View>
      </SbEntrance>
      {detailView.notableRowViews.length > 0 && (
        <SbEntrance order={NOTABLE_ORDER}>
          <SbSectionHeader title="꼭 볼 것" caption="널리 쓰는 제품 · AI 가 중요하다고 본 것" />
          <View style={styles.cards}>
            {detailView.notableRowViews.map((rowView) => (
              <ArticleFeedItem key={rowView.id} rowView={rowView} presentation="card" />
            ))}
          </View>
        </SbEntrance>
      )}
      {detailView.fieldSections.length > 0 && (
        <SbEntrance order={FIELD_ORDER}>
          <SbSectionHeader title="분야별" caption="눌러서 펼쳐요" />
          {detailView.fieldSections.map((section) => {
            const isExpanded = expandedFieldCodes.includes(section.fieldCode);
            return (
              <View key={section.fieldCode}>
                <SbDisclosureRow
                  title={section.title}
                  detail={section.detail}
                  trailingLabel={section.countLabel}
                  isExpanded={isExpanded}
                  accessibilityLabel={`${section.title} ${section.countLabel}, ${isExpanded ? '접기' : '펼치기'}`}
                  onPress={() => toggleField(section.fieldCode)}
                />
                {isExpanded && (
                  <SbReveal>
                    {section.rowViews.map((rowView) => (
                      <ArticleFeedItem key={rowView.id} rowView={rowView} presentation="oneLine" />
                    ))}
                  </SbReveal>
                )}
              </View>
            );
          })}
        </SbEntrance>
      )}
    </>
  );
}

const styles = StyleSheet.create({
  heading: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small, paddingBottom: sbSpacing.compact },
  tiles: { flexDirection: 'row', gap: sbSpacing.tileGap, paddingHorizontal: sbSpacing.tileHorizontal },
  cards: { gap: sbSpacing.small },
});
