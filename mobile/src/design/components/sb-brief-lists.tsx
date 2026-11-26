import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

const NUMBER_MARK_SIZE = 22;

/** 번호 목록 (기사 상세의 핵심): 둥근 번호 + 한 줄씩 */
export function SbNumberedList({ items }: { items: string[] }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.list}>
      {items.map((item, itemIndex) => (
        <View key={`${itemIndex}-${item}`} style={styles.numberedItem}>
          <View style={styles.numberMark}>
            <SbText variant="capsLabel" color={colors.onFeature}>
              {String(itemIndex + 1)}
            </SbText>
          </View>
          <View style={styles.itemText}>
            <SbText variant="reading" color={colors.label}>
              {item}
            </SbText>
          </View>
        </View>
      ))}
    </View>
  );
}

export interface SbFact {
  label: string;
  value: string;
}

interface SbFactTableProps {
  facts: SbFact[];
  /** 접힌 긴 값을 펼치는·다시 접는 버튼 문구 (예: 더 보기, 접기) */
  expandLabel: string;
  collapseLabel: string;
}

// 값 길이 기준 (글자 수): 이보다 길면 라벨 아래 전체 폭으로 쌓고, 더 길면 접어서 보여 줌
const STACKED_VALUE_MINIMUM_LENGTH = 60;
const COLLAPSED_VALUE_MINIMUM_LENGTH = 240;
// 접힌 값에 보이는 줄 수
const COLLAPSED_VALUE_LINES = 5;
// 펼치기 버튼(글 한 줄)의 터치 영역을 넓히는 위아래 여유
const TOGGLE_HIT_SLOP = { top: sbSpacing.compact, bottom: sbSpacing.compact };

/** 확인된 정보 표 (기사 상세): 값은 받은 그대로. 짧은 값은 이름 옆 고정폭, 긴 값은 이름 아래 전체 폭 본문 글꼴 */
export function SbFactTable({ facts, expandLabel, collapseLabel }: SbFactTableProps) {
  const styles = useSbStyles(createStyles);
  return (
    <View>
      {facts.map((fact, factIndex) => (
        <View key={`${factIndex}-${fact.label}`} style={factIndex > 0 && styles.factSeparator}>
          {fact.value.length > STACKED_VALUE_MINIMUM_LENGTH ? (
            <SbStackedFact fact={fact} expandLabel={expandLabel} collapseLabel={collapseLabel} />
          ) : (
            <SbInlineFact fact={fact} />
          )}
        </View>
      ))}
    </View>
  );
}

/** 짧은 값 한 줄 (버전·식별자·날짜): 왼쪽 이름 + 오른쪽 고정폭 값 */
function SbInlineFact({ fact }: { fact: SbFact }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.inlineFact} accessible accessibilityLabel={`${fact.label}, ${fact.value}`}>
      <View style={styles.inlineFactLabel}>
        <SbText variant="caption" color={colors.tertiaryLabel}>
          {fact.label}
        </SbText>
      </View>
      <View style={styles.inlineFactValue}>
        <SbText variant="factValue" color={colors.label}>
          {fact.value}
        </SbText>
      </View>
    </View>
  );
}

/** 긴 값 (문장): 이름 > 전체 폭 값. 아주 긴 값은 5줄에서 접고 버튼으로 펼침 */
function SbStackedFact({ fact, expandLabel, collapseLabel }: { fact: SbFact; expandLabel: string; collapseLabel: string }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const [isExpanded, setIsExpanded] = useState(false);
  const isCollapsible = fact.value.length > COLLAPSED_VALUE_MINIMUM_LENGTH;
  const toggleLabel = isExpanded ? collapseLabel : expandLabel;
  return (
    <View style={styles.stackedFact}>
      <SbText variant="caption" color={colors.tertiaryLabel}>
        {fact.label}
      </SbText>
      <SbText variant="body" color={colors.label} numberOfLines={isCollapsible && !isExpanded ? COLLAPSED_VALUE_LINES : undefined}>
        {fact.value}
      </SbText>
      {isCollapsible && (
        <View style={styles.toggle}>
          <SbPressable
            accessibilityLabel={`${fact.label} ${toggleLabel}`}
            accessibilityState={{ expanded: isExpanded }}
            hitSlop={TOGGLE_HIT_SLOP}
            onPress={() => setIsExpanded(!isExpanded)}
            feedback="dim">
            <SbText variant="button" color={colors.secondaryLabel}>
              {toggleLabel}
            </SbText>
          </SbPressable>
        </View>
      )}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    list: { gap: sbSpacing.compact },
    numberedItem: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.compact },
    numberMark: {
      width: NUMBER_MARK_SIZE,
      height: NUMBER_MARK_SIZE,
      borderRadius: sbRadius.pill,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: colors.feature,
    },
    itemText: { flex: 1 },
    factSeparator: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.separator },
    inlineFact: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.compact, paddingVertical: sbSpacing.small },
    inlineFactLabel: { flex: 2 },
    inlineFactValue: { flex: 3 },
    stackedFact: { gap: sbSpacing.extraSmall, paddingVertical: sbSpacing.small },
    toggle: { alignSelf: 'flex-start', paddingTop: sbSpacing.extraSmall },
  });
