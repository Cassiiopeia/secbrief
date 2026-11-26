import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated from 'react-native-reanimated';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMotion, sbRadius, sbSize, sbSpacing } from '../tokens';
import { SbEntrance } from './sb-entrance';
import { SbFilledButton } from './sb-filled-button';
import { sbRevealEntering } from './sb-layout-motion';
import { SbPressable } from './sb-pressable';
import { SbStateIllustration } from './sb-state-illustration';
import { type SbStateIllustrationKind, toSbErrorIllustrationKind } from './sb-state-illustration-sources';
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

/** 오류 상태: 그림(연결 끊김 / 문제 생김) + 안내 문구 + 다시 시도 + 에러 코드 (모든 탭 공통) */
export function SbErrorState({ message, errorCode, onRetry }: SbErrorStateProps) {
  const { colors } = useSbTheme();
  return (
    <SbStateFrame illustration={toSbErrorIllustrationKind(errorCode)}>
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

/** 구역 안 오류 한 줄 (여러 구역이 쌓인 화면): 아이콘 + 안내 문구·에러 코드 + 다시 시도. 붙을 때 살짝 내려오며 나타남 */
export function SbInlineErrorState({ message, errorCode, onRetry }: SbErrorStateProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <Animated.View style={styles.inlineWrap} entering={sbRevealEntering}>
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
    </Animated.View>
  );
}

interface SbEmptyStateProps {
  title: string;
  description?: string;
  /** 상태 그림 (기본: 찾지 못함 빈 상자) */
  illustration?: SbStateIllustrationKind;
  /** 다른 보기로 넘어가는 버튼 (예: 전체 보기). 둘 다 줘야 보임 */
  actionLabel?: string;
  onActionPress?: () => void;
}

/** 빈 상태: 로딩과 구분되는 그림 + 제목 + 한 줄 설명 + (다른 보기로 가는 버튼) (모든 탭 공통) */
export function SbEmptyState({ title, description, illustration = 'notFound', actionLabel, onActionPress }: SbEmptyStateProps) {
  const { colors } = useSbTheme();
  return (
    <SbStateFrame illustration={illustration}>
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

/** 상태 화면 공통 틀: 남은 자리 가운데에 그림(스프링으로 나타남) > 글·버튼(한 박자 뒤 올라옴) */
function SbStateFrame({ illustration, children }: { illustration: SbStateIllustrationKind; children: ReactNode }) {
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.wrap}>
      <View style={styles.content}>
        <SbStateIllustration kind={illustration} />
        <SbEntrance order={sbMotion.stateTextOrder}>
          <View style={styles.texts}>{children}</View>
        </SbEntrance>
      </View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { flex: 1, justifyContent: 'center', padding: sbSpacing.extraLarge },
    content: { alignItems: 'center', gap: sbSpacing.small },
    texts: { alignItems: 'center', gap: sbSpacing.medium },
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
