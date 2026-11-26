import { useQuery, useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';

import { hasAnyAlertInterest } from '@/data/models/alert-interest';
import { readIsInterestPromptDismissed, saveInterestPromptDismissed } from '@/data/sources/alert-interest-storage';

import { APP_ROUTES } from '../navigation/app-routes';
import { useAlertInterests } from './use-alert-interests';

const INTEREST_PROMPT_DISMISSED_QUERY_KEY = ['interest-prompt-dismissed'] as const;

/**
 * 관심 기술 처음 안내 (한 번만): 아직 아무것도 안 골랐고 닫은 적이 없을 때만 보임
 * 고르기를 누르면 관심 기술 화면, 나중에를 누르면 다시 안 보임 (기기에 저장)
 */
export function useInterestPrompt() {
  const queryClient = useQueryClient();
  const interests = useAlertInterests();
  const dismissedQuery = useQuery({ queryKey: INTEREST_PROMPT_DISMISSED_QUERY_KEY, queryFn: readIsInterestPromptDismissed });
  const dismiss = () => {
    queryClient.setQueryData(INTEREST_PROMPT_DISMISSED_QUERY_KEY, true);
    void saveInterestPromptDismissed();
  };
  return {
    // 저장값을 읽기 전에는 숨김 (닫은 안내가 잠깐 보였다 사라지지 않게)
    isVisible: dismissedQuery.data === false && !hasAnyAlertInterest(interests),
    onChoosePressed: () => {
      dismiss();
      router.push(APP_ROUTES.interests);
    },
    onDismissPressed: dismiss,
  };
}
