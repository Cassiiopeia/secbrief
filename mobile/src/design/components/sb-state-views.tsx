import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSize, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbEntrance } from './sb-entrance';
import { SbFilledButton } from './sb-filled-button';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbErrorStateProps {
  /** 사용자에게 보여 줄 문구 */
  message: string;
  /** 제보 추적용 에러 코드 (예: APP_UNKNOWN) */
  errorCode: string;
  onRetry: () => void;
}

const RETRY_LABEL = '다시 시도';
const INLINE_ERROR_ICON_SIZE = 18;

/** 오류 상태: 아이콘 + 안내 문구 + 다시 시도 + 에러 코드 (모든 탭 공통) */
export function SbErrorState({ message, errorCode, onRetry }: SbErrorStateProps) {
  const { colors } = useSbTheme();
  return (
    <SbStateFrame iconName="error-outline">
      <View accessibilityRole="alert">
        <SbText variant="headline" color={colors.label} isCentered>
          {message}
        </SbText>
      </View>
      <SbFilledButton label={RETRY_LABEL} onPress={onRetry} />
      <SbText variant="timeLabel" color={colors.tertiaryLabel}>
        {errorCode}
      </SbText>
    </SbStateFrame>
  );
}

/** 구역 안 오류 한 줄 (여러 구역이 쌓인 화면): 아이콘 + 안내 문구·에러 코드 + 다시 시도 */
export function SbInlineErrorState({ message, errorCode, onRetry }: SbErrorStateProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.inlineWrap}>
      <View style={styles.inlineError}>
        <MaterialIcons name="error-outline" size={INLINE_ERROR_ICON_SIZE} color={colors.secondaryLabel} />
        <View style={styles.inlineText} accessibilityRole="alert">
          <SbText variant="caption" color={colors.label}>
            {message}
          </SbText>
          <SbText variant="timeLabel" color={colors.tertiaryLabel}>
            {errorCode}
          </SbText>
        </View>
        <SbPressable accessibilityLabel={RETRY_LABEL} onPress={onRetry} feedback="dim">
          <View style={styles.inlineRetry}>
            <SbText variant="button" color={colors.label}>
              {RETRY_LABEL}
            </SbText>
          </View>
        </SbPressable>
      </View>
    </View>
  );
}

interface SbEmptyStateProps {
  title: string;
  description?: string;
  iconName?: SbIconName;
  /** 다른 보기로 넘어가는 버튼 (예: 전체 보기). 둘 다 줘야 보임 */
  actionLabel?: string;
  onActionPress?: () => void;
}

/** 빈 상태: 로딩과 구분되는 아이콘 + 제목 + 한 줄 설명 + (다른 보기로 가는 버튼) (모든 탭 공통) */
export function SbEmptyState({ title, description, iconName = 'inbox', actionLabel, onActionPress }: SbEmptyStateProps) {
  const { colors } = useSbTheme();
  return (
    <SbStateFrame iconName={iconName}>
      <SbText variant="headline" color={colors.label} isCentered>
        {title}
      </SbText>
      {description !== undefined && (
        <SbText variant="body" color={colors.tertiaryLabel} isCentered>
          {description}
        </SbText>
      )}
      {actionLabel !== undefined && onActionPress !== undefined && <SbFilledButton label={actionLabel} onPress={onActionPress} />}
    </SbStateFrame>
  );
}

/** 상태 화면 공통 틀: 남은 자리 가운데에 둥근 바탕 아이콘 + 내용. 뼈대 뒤에 부드럽게 나타남 */
function SbStateFrame({ iconName, children }: { iconName: SbIconName; children: ReactNode }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.wrap}>
      <SbEntrance order={0}>
        <View style={styles.content}>
          <View style={styles.iconBackdrop}>
            <MaterialIcons name={iconName} size={sbSize.stateIcon} color={colors.secondaryLabel} />
          </View>
          {children}
        </View>
      </SbEntrance>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { flex: 1, justifyContent: 'center', padding: sbSpacing.extraLarge },
    content: { alignItems: 'center', gap: sbSpacing.medium },
    iconBackdrop: {
      width: sbSize.stateIconBackdrop,
      height: sbSize.stateIconBackdrop,
      borderRadius: sbRadius.pill,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: colors.surfaceRaised,
    },
    inlineWrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingVertical: sbSpacing.small },
    inlineError: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      paddingLeft: sbSpacing.compact,
      borderRadius: sbRadius.button,
      backgroundColor: colors.surfaceRaised,
    },
    inlineText: { flex: 1, gap: sbSpacing.hairline, paddingVertical: sbSpacing.compact },
    inlineRetry: { minHeight: sbSize.minimumTouchTarget, justifyContent: 'center', paddingHorizontal: sbSpacing.compact },
  });
