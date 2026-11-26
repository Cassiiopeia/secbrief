import { DateTimePicker } from '@expo/ui/community/datetime-picker';
import { useState } from 'react';
import { Platform } from 'react-native';

import { toClockTime } from '@/core/format/briefing-date-formatter';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbMaximumLines } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbPressable } from './sb-pressable';
import { SbRowFrame } from './sb-row-frame';
import { SbText } from './sb-text';

interface SbTimePickerRowProps {
  title: string;
  /** 고른 시각 "HH:mm" (24시간) */
  timeOfDay: string;
  /** 행에 보일 시각 글자 (예: 오후 10:00) */
  timeLabel: string;
  isDisabled?: boolean;
  accessibilityLabel: string;
  onTimeSelected: (timeOfDay: string) => void;
}

// 시각 글자 "HH:mm" > 오늘 날짜의 그 시각 (선택기는 Date 를 주고받음, 반대 방향은 toClockTime)
function toDate(timeOfDay: string): Date {
  const [hour, minute] = timeOfDay.split(':').map(Number);
  const date = new Date();
  date.setHours(hour, minute, 0, 0);
  return date;
}

/**
 * 시각 고르는 설정 행 (SbListRow 와 같은 틀): 제목 + 오른쪽 시각
 * 플랫폼 3갈래: iOS 행 안 작은 선택기, Android 누르면 시각 창, 웹 시각 글자만 (@expo/ui 선택기가 웹을 지원하지 않음)
 */
export function SbTimePickerRow({ title, timeOfDay, timeLabel, isDisabled = false, accessibilityLabel, onTimeSelected }: SbTimePickerRowProps) {
  const { colors, isDark } = useSbTheme();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const onValueChange = (date: Date) => {
    setIsDialogOpen(false);
    const selectedTimeOfDay = toClockTime(date);
    if (selectedTimeOfDay === timeOfDay) return;
    playSbHaptic('selection');
    onTimeSelected(selectedTimeOfDay);
  };
  const timeText = (
    <SbText variant="rowTitle" color={isDisabled ? colors.tertiaryLabel : colors.label} numberOfLines={sbMaximumLines.singleLine}>
      {timeLabel}
    </SbText>
  );

  if (Platform.OS === 'ios') {
    // 행을 한 덩어리로 묶지 않음 (선택기가 읽기 도우미로 직접 조작돼야 함)
    return (
      <SbRowFrame
        title={title}
        isTitleDimmed={isDisabled}
        trailing={
          <DateTimePicker
            value={toDate(timeOfDay)}
            mode="time"
            display="compact"
            disabled={isDisabled}
            locale="ko_KR"
            themeVariant={isDark ? 'dark' : 'light'}
            onValueChange={(_event, date) => onValueChange(date)}
          />
        }
      />
    );
  }
  if (Platform.OS === 'android') {
    return (
      <>
        <SbPressable accessibilityLabel={accessibilityLabel} onPress={isDisabled ? undefined : () => setIsDialogOpen(true)} feedback="dim">
          <SbRowFrame title={title} isTitleDimmed={isDisabled} trailing={timeText} />
        </SbPressable>
        {isDialogOpen && (
          // 창은 그리는 순간 열리고, 고르거나 닫으면 지움
          <DateTimePicker
            value={toDate(timeOfDay)}
            mode="time"
            presentation="dialog"
            is24Hour={false}
            positiveButton={{ label: '확인' }}
            negativeButton={{ label: '취소' }}
            onValueChange={(_event, date) => onValueChange(date)}
            onDismiss={() => setIsDialogOpen(false)}
          />
        )}
      </>
    );
  }
  return <SbRowFrame title={title} isTitleDimmed={isDisabled} trailing={timeText} accessibilityLabel={accessibilityLabel} />;
}
