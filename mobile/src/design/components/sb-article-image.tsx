import { Image } from 'expo-image';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbTheme } from '../theme/use-sb-theme';
import { sbMotion } from '../tokens';
import { SbCoverArt, type SbCoverKind, type SbCoverTone, toSbCoverPalette } from './sb-cover-art';

interface SbArticleImageProps {
  /** 소식 대표 이미지 주소 (없으면 커버 그림만) */
  imageUrl?: string;
  /** 이미지가 없거나 못 불러올 때 보이는 커버 그림 */
  coverKind: SbCoverKind;
  coverTone: SbCoverTone;
  /** 과녁 고리 맥박 여부 (커버 그림이 보일 때만) */
  isPulsing?: boolean;
}

/**
 * 소식 그림: 커버 그림을 먼저 깔고 그 위에 대표 이미지를 겹쳐 띄움 (부모 크기를 가득 채움)
 * 이미지가 없음·불러오는 중·실패 모두 같은 자리에 커버 그림이 보여 배치가 흔들리지 않음
 */
export function SbArticleImage({ imageUrl, coverKind, coverTone, isPulsing = false }: SbArticleImageProps) {
  const { colors } = useSbTheme();
  const isMotionReduced = useIsMotionReduced();
  // 불러오기에 실패한 주소 (주소가 바뀌면 다시 시도)
  const [failedImageUrl, setFailedImageUrl] = useState<string>();
  // 다 나타난 이미지의 주소 (그 뒤에는 밑의 커버 그림을 치움: 둥근 모서리에 커버 색이 비치지 않게)
  const [settledImageUrl, setSettledImageUrl] = useState<string>();
  const isImageShown = imageUrl !== undefined && imageUrl !== failedImageUrl;
  const isCoverShown = !isImageShown || imageUrl !== settledImageUrl;
  const onImageLoaded = () => {
    setTimeout(() => setSettledImageUrl(imageUrl), isMotionReduced ? 0 : sbMotion.imageFadeMilliseconds);
  };
  return (
    <View style={styles.fill}>
      {isCoverShown && (
        <SbCoverArt kind={coverKind} palette={toSbCoverPalette(colors, coverTone)} isPulsing={isPulsing && !isImageShown} />
      )}
      {isImageShown && (
        <Image
          source={imageUrl}
          style={styles.image}
          contentFit="cover"
          cachePolicy="disk"
          recyclingKey={imageUrl}
          transition={isMotionReduced ? null : sbMotion.imageFadeMilliseconds}
          // 장식 그림 (제목이 같은 내용을 읽어 줌)
          accessible={false}
          onLoad={onImageLoaded}
          onError={() => setFailedImageUrl(imageUrl)}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  fill: { width: '100%', height: '100%' },
  image: { position: 'absolute', top: 0, right: 0, bottom: 0, left: 0 },
});
