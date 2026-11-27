import { SbPromptCard } from '@/design';

import { useInterestPrompt } from './use-interest-prompt';

/** 관심 기술 처음 안내 카드 (한 번만, 보일 때만 그림: useInterestPrompt) */
export function InterestPromptCard() {
  const interestPrompt = useInterestPrompt();
  if (!interestPrompt.isVisible) return null;
  return (
    <SbPromptCard
      title="관심 기술을 골라 보세요"
      message="쓰는 제품·분야·생태계를 고르면 그 소식과 악용 중·KISA 긴급만 알려 드려요."
      primaryLabel="관심 기술 고르기"
      dismissLabel="나중에"
      onPrimaryPress={interestPrompt.onChoosePressed}
      onDismissPress={interestPrompt.onDismissPressed}
    />
  );
}
