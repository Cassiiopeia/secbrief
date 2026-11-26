import { router } from 'expo-router';

import { toKoreanClockTime } from '@/core/format/notification-date-formatter';
import { SbInlineErrorState, SbListRow, SbReveal, SbSectionHeader, SbSwitchRow, SbTimePickerRow } from '@/design';

import { useTagCatalogIndex } from '../catalog/use-tag-catalog';
import { APP_ROUTES } from '../navigation/app-routes';
import { countPickedInterests, toInterestSummaryLines } from './interest-view-state';
import { useAlertInterests } from './use-alert-interests';
import { useAlertDigestSettingsEditor } from './use-alert-interest-settings';

const INTEREST_ROW_TITLE = '관심 기술';
const INTEREST_ROW_DETAIL = '고른 제품·분야·생태계 소식만 알려 드려요';

/** 더보기 관심 기술 행 (#93 시안 A①): 이름 + 설명 + 고른 수 > 요약 3줄(제품·분야·생태계). 누르면 관심 기술 화면 */
export function InterestEntrySection() {
  const catalogIndex = useTagCatalogIndex();
  const interests = useAlertInterests();
  const pickedCount = countPickedInterests(interests, catalogIndex);
  const summaryLines = toInterestSummaryLines(interests, catalogIndex);
  return (
    <SbListRow
      title={INTEREST_ROW_TITLE}
      detail={[INTEREST_ROW_DETAIL, ...summaryLines].join('\n')}
      trailingLabel={`${pickedCount}개`}
      trailingIconName="chevron-right"
      accessibilityLabel={`${INTEREST_ROW_TITLE} ${pickedCount}개 열기, ${summaryLines.join(', ')}`}
      onPress={() => router.push(APP_ROUTES.interests)}
    />
  );
}

/**
 * 아침 요약 구역 (#93 시안 C①): 아침 요약 받기 스위치(끄면 관심 소식도 바로 받기) > (켰을 때) 받는 시각
 * 악용 중·KISA 긴급은 이 설정과 상관없이 바로 알림
 */
export function DigestSettingsSection() {
  const editor = useAlertDigestSettingsEditor();
  const isDigest = editor.digest.deliveryMode === 'DIGEST';
  const timeLabel = toKoreanClockTime(editor.digest.digestTime);
  return (
    <>
      <SbSectionHeader title="아침 요약" caption="급하지 않은 관심 소식을 하루 한 번 모아 드려요" />
      <SbSwitchRow
        title="아침 요약 받기"
        detail={isDigest ? `관심 소식은 ${timeLabel}에 모아서 받아요` : '관심 소식도 생기는 즉시 받아요'}
        isOn={isDigest}
        isDisabled={editor.isSaving}
        accessibilityLabel="아침 요약 받기"
        onToggled={(isOn) => editor.saveDigest({ ...editor.digest, deliveryMode: isOn ? 'DIGEST' : 'IMMEDIATE' })}
      />
      {isDigest && (
        <SbReveal>
          <SbTimePickerRow
            title="받는 시각"
            timeOfDay={editor.digest.digestTime}
            timeLabel={timeLabel}
            isDisabled={editor.isSaving}
            accessibilityLabel={`아침 요약 받는 시각 ${timeLabel}, 바꾸기`}
            onTimeSelected={(digestTime) => editor.saveDigest({ ...editor.digest, digestTime })}
          />
        </SbReveal>
      )}
      {editor.saveFailure !== undefined && (
        <SbInlineErrorState
          message={editor.saveFailure.userMessage}
          errorCode={editor.saveFailure.errorCode}
          onRetry={() => editor.saveDigest(editor.digest)}
        />
      )}
    </>
  );
}
