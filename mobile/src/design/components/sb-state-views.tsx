import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbFilledButton } from './sb-filled-button';
import { SbText } from './sb-text';

const STATE_ICON_SIZE = 36;

interface SbErrorStateProps {
  /** 사용자에게 보여 줄 문구 */
  message: string;
  /** 제보 추적용 에러 코드 (예: APP_UNKNOWN) */
  errorCode: string;
  onRetry: () => void;
}

/** 오류 상태: 안내 문구 + 다시 시도 + 에러 코드 */
export function SbErrorState({ message, errorCode, onRetry }: SbErrorStateProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.wrap}>
      <MaterialIcons name="error-outline" size={STATE_ICON_SIZE} color={colors.tertiaryLabel} />
      <SbText variant="headline" color={colors.label} isCentered>
        {message}
      </SbText>
      <SbFilledButton label="다시 시도" onPress={onRetry} />
      <SbText variant="timeLabel" color={colors.tertiaryLabel}>
        {errorCode}
      </SbText>
    </View>
  );
}

interface SbEmptyStateProps {
  title: string;
  description?: string;
  iconName?: SbIconName;
}

/** 빈 상태: 로딩과 구분되는 아이콘 + 제목 + 한 줄 설명 */
export function SbEmptyState({ title, description, iconName = 'inbox' }: SbEmptyStateProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.wrap}>
      <MaterialIcons name={iconName} size={STATE_ICON_SIZE} color={colors.tertiaryLabel} />
      <SbText variant="headline" color={colors.label} isCentered>
        {title}
      </SbText>
      {description !== undefined && (
        <SbText variant="body" color={colors.tertiaryLabel} isCentered>
          {description}
        </SbText>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: sbSpacing.medium,
    padding: sbSpacing.extraLarge,
  },
});
