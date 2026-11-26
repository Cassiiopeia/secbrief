import { Image } from 'expo-image';
import { useState } from 'react';
import { StyleSheet } from 'react-native';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbMotion } from '../tokens';
import { toSbShownArticleImageUrl } from './sb-article-image-layout';

/** 소식 대표 이미지 상태: 보일 주소(없거나 실패면 없음) + 실패 알림 */
export interface SbArticleImageState {
  shownImageUrl?: string;
  onImageFailed: () => void;
}

/**
 * 소식 대표 이미지 주소 > 보일 주소 (불러오기 실패한 주소는 없음으로, 주소가 바뀌면 다시 시도)
 * 이미지를 품는 부품이 이 값으로 그림 자리 유무와 제목 줄 수를 정함 (실패하면 글만 있는 배치)
 */
export function useSbArticleImageState(imageUrl: string | undefined): SbArticleImageState {
  const [failedImageUrl, setFailedImageUrl] = useState<string>();
  return { shownImageUrl: toSbShownArticleImageUrl(imageUrl, failedImageUrl), onImageFailed: () => setFailedImageUrl(imageUrl) };
}

interface SbArticleImageProps {
  /** 소식 대표 이미지 주소 (실제 이미지만, 대신 보일 그림 없음) */
  imageUrl: string;
  /** 불러오기 실패 (부모가 그림 자리를 치우고 글만 있는 배치로) */
  onImageFailed: () => void;
}

/** 소식 대표 이미지: 부모 크기를 가득 채움, 불러오는 동안은 바탕색 */
export function SbArticleImage({ imageUrl, onImageFailed }: SbArticleImageProps) {
  const styles = useSbStyles(createStyles);
  const isMotionReduced = useIsMotionReduced();
  return (
    <Image
      source={imageUrl}
      style={styles.image}
      contentFit="cover"
      cachePolicy="disk"
      recyclingKey={imageUrl}
      transition={isMotionReduced ? null : sbMotion.imageFadeMilliseconds}
      // 장식 그림 (제목이 같은 내용을 읽어 줌)
      accessible={false}
      onError={onImageFailed}
    />
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    image: { width: '100%', height: '100%', backgroundColor: colors.surfaceRaised },
  });
