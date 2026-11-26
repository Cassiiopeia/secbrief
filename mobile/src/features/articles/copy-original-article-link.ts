import * as Clipboard from 'expo-clipboard';

import { showSbToast } from '@/design';

export const ORIGINAL_LINK_COPIED_MESSAGE = '원문 링크를 복사했어요';
export const ORIGINAL_LINK_COPY_FAILED_MESSAGE = '링크를 복사하지 못했어요. 원문 보기로 열어 주세요';

/** 원문 링크 복사 > 결과 알림 (성공·실패 모두 토스트) */
export async function copyOriginalArticleLink(originalUrl: string): Promise<void> {
  try {
    await Clipboard.setStringAsync(originalUrl);
    showSbToast(ORIGINAL_LINK_COPIED_MESSAGE);
  } catch {
    showSbToast(ORIGINAL_LINK_COPY_FAILED_MESSAGE);
  }
}
