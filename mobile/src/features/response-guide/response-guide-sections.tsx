import { View } from 'react-native';

import { SbBriefCard, SbSectionHeader, SbText, useSbTheme } from '@/design';

import { ResponseGuideRow } from './response-guide-row';
import { RESPONSE_GUIDE_HEADER_TEXTS } from './response-guide-texts';
import type { ResponseGuideSectionView } from './response-guide-view';

/**
 * 대응 모아보기 (기사 상세): 머리(대응 모아보기) > 구역 카드(공식 대응은 강조 테두리 > 확인된 취약점 정보 > 관련 기사)
 * 카드마다 무엇을 모았는지 한 줄 > 행 목록 > (외 N건). 구역이 없으면 그리지 않음 (부모가 함께 볼 소식으로 대신함)
 */
export function ResponseGuideSections({ sectionViews }: { sectionViews: ResponseGuideSectionView[] }) {
  const { colors } = useSbTheme();
  if (sectionViews.length === 0) return null;
  return (
    <View>
      <SbSectionHeader title={RESPONSE_GUIDE_HEADER_TEXTS.title} caption={RESPONSE_GUIDE_HEADER_TEXTS.caption} />
      {sectionViews.map((sectionView) => (
        <SbBriefCard
          key={sectionView.key}
          title={sectionView.title}
          iconName={sectionView.iconName}
          tone={sectionView.isEmphasized ? 'emphasis' : 'plain'}>
          <SbText variant="caption" color={colors.tertiaryLabel}>
            {sectionView.caption}
          </SbText>
          <View role="list">
            {sectionView.rows.map((rowView, rowIndex) => (
              <ResponseGuideRow key={rowView.key} rowView={rowView} isFirst={rowIndex === 0} />
            ))}
          </View>
          {sectionView.moreCountLabel !== undefined && (
            <SbText variant="caption" color={colors.tertiaryLabel}>
              {sectionView.moreCountLabel}
            </SbText>
          )}
        </SbBriefCard>
      ))}
    </View>
  );
}
