import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import type { AppNotificationDetail } from '@/data/models/app-notification';
import { SbFilledButton, SbText, sbSize, sbSpacing, useSbTheme } from '@/design';

import { openOriginalArticle } from '../articles/open-original-article';
import { toNoticeDetailView } from './notification-detail-view';

/** 공지사항 상세 본문: (고정) > 제목 > 날짜 > 본문(줄바꿈 그대로) > (링크 버튼) */
export function NoticeDetailContent({ detail }: { detail: AppNotificationDetail }) {
  const { colors } = useSbTheme();
  const detailView = toNoticeDetailView(detail);
  const linkUrl = detailView.linkUrl;
  return (
    <View style={styles.content}>
      {detailView.isPinned && (
        <View style={styles.pinned}>
          <MaterialIcons name="push-pin" size={sbSize.rowIcon} color={colors.urgent} />
          <SbText variant="capsLabel" color={colors.urgent}>
            고정된 공지
          </SbText>
        </View>
      )}
      <View accessibilityRole="header">
        <SbText variant="detailTitle" color={colors.label}>
          {detailView.title}
        </SbText>
      </View>
      <SbText variant="timeLabel" color={colors.tertiaryLabel}>
        {detailView.dateLabel}
      </SbText>
      <View style={styles.body}>
        <SbText variant="reading" color={colors.secondaryLabel}>
          {detailView.body}
        </SbText>
      </View>
      {linkUrl !== undefined && (
        <SbFilledButton
          label="자세히 보기"
          trailingIconName="open-in-new"
          accessibilityLabel="공지 자세히 보기, 브라우저로 열기"
          onPress={() => openOriginalArticle(linkUrl)}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  content: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small },
  pinned: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.hairline },
  body: { paddingVertical: sbSpacing.medium },
});
