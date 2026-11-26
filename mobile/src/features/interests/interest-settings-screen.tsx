import { router } from 'expo-router';
import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import type { AlertInterests } from '@/data/models/alert-interest';
import {
  SbBottomActionBar,
  SbFilledButton,
  SbInlineErrorState,
  SbPressable,
  SbScreen,
  SbScreenHeader,
  SbSegmentedControl,
  type SbSegmentOption,
  SbText,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { useTagCatalogIndex } from '../catalog/use-tag-catalog';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { InterestPickerTabBody } from './interest-picker-tab-body';
import { type InterestPickerTab, TAB_LABELS, toEstimateLine, toToggledInterests } from './interest-view-state';
import { useAlertDigestSettings, useAlertInterests } from './use-alert-interests';
import { useAlertEstimate, useAlertInterestSaver } from './use-alert-interest-settings';

const TAB_OPTIONS: SbSegmentOption<InterestPickerTab>[] = (['product', 'field', 'ecosystem'] as const).map((tab) => ({
  value: tab,
  label: TAB_LABELS[tab],
}));

// 저장하거나 나중에 고를 때 돌아가기 (쌓인 화면이 없으면 홈)
const closeInterestScreen = () => (router.canGoBack() ? router.back() : goBackOrHome());

/**
 * 관심 기술 화면 (#93 시안 A②, 탭 밖 스택): 머리 > 질문 > 칸(제품·분야·생태계) > 칸 본문 > 아래 고정(어제 기준 알림 추정 > 저장하기 > 나중에)
 * 고르는 동안은 화면 안에만 두고 저장하기를 눌러야 기기·서버에 저장
 */
export function InterestSettingsScreen() {
  const { colors } = useSbTheme();
  const catalogIndex = useTagCatalogIndex();
  const storedInterests = useAlertInterests();
  const digest = useAlertDigestSettings();
  const [draftInterests, setDraftInterests] = useState<AlertInterests>();
  const [pickerTab, setPickerTab] = useState<InterestPickerTab>('product');
  const interests = draftInterests ?? storedInterests;
  const estimate = useAlertEstimate(interests, digest);
  const saver = useAlertInterestSaver(closeInterestScreen);

  return (
    <SbScreen>
      <SbScreenHeader title="관심 기술" backAccessibilityLabel="뒤로" onBackPress={closeInterestScreen} />
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <View style={styles.question}>
          <View accessibilityRole="header">
            <SbText variant="detailTitle" color={colors.label}>
              어떤 기술을 쓰세요?
            </SbText>
          </View>
          <SbText variant="caption" color={colors.tertiaryLabel}>
            고른 기술의 취약점만 알려 드려요. 악용 중인 취약점은 늘 알려 드려요.
          </SbText>
        </View>
        <SbSegmentedControl options={TAB_OPTIONS} selectedValue={pickerTab} onSelected={setPickerTab} />
        <InterestPickerTabBody
          tab={pickerTab}
          interests={interests}
          catalogIndex={catalogIndex}
          onToggled={(tab, code) => setDraftInterests(toToggledInterests(interests, tab, code))}
        />
      </ScrollView>
      <SbBottomActionBar
        primaryAction={
      <View style={styles.bottom}>
        <SbText variant="caption" color={colors.secondaryLabel} isCentered>
          {toEstimateLine(interests, catalogIndex, estimate)}
        </SbText>
        {saver.saveFailure !== undefined && (
          <SbInlineErrorState
            message={saver.saveFailure.userMessage}
            errorCode={saver.saveFailure.errorCode}
            onRetry={() => saver.save(interests, digest)}
          />
        )}
        <SbFilledButton label={saver.isSaving ? '저장하고 있어요' : '저장하기'} onPress={() => saver.save(interests, digest)} />
        <SbPressable accessibilityLabel="나중에 고를게요" accessibilityRole="button" onPress={closeInterestScreen} feedback="dim">
          <View style={styles.laterButton}>
            <SbText variant="button" color={colors.tertiaryLabel} isCentered>
              나중에 고를게요
            </SbText>
          </View>
        </SbPressable>
      </View>
        }
      />
    </SbScreen>
  );
}

const styles = StyleSheet.create({
  content: { paddingBottom: sbSpacing.large },
  question: { gap: sbSpacing.extraSmall, paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.extraSmall, paddingBottom: sbSpacing.small },
  bottom: { gap: sbSpacing.small },
  laterButton: { paddingVertical: sbSpacing.small },
});
