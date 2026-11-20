import { useEffect, useState } from 'react';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { type SbTextVariant, sbMotion, toCountUpProgress } from '../tokens';
import { SbText } from './sb-text';

interface SbCountUpTextProps {
  targetValue: number;
  /** 숫자 뒤에 붙는 단위 (예: 일) */
  unit: string;
  variant: SbTextVariant;
  color: string;
}

/** 0 에서 목표값까지 세어 올라가는 숫자. 동작 줄이기가 켜져 있으면 목표값을 바로 보여 줌 */
export function SbCountUpText({ targetValue, unit, variant, color }: SbCountUpTextProps) {
  const isMotionReduced = useIsMotionReduced();
  const [shownValue, setShownValue] = useState(isMotionReduced ? targetValue : 0);

  useEffect(() => {
    if (isMotionReduced) {
      setShownValue(targetValue);
      return;
    }
    const startedAt = Date.now();
    let frameId = requestAnimationFrame(function advance() {
      const linearProgress = Math.min((Date.now() - startedAt) / sbMotion.countUpMilliseconds, 1);
      setShownValue(Math.round(targetValue * toCountUpProgress(linearProgress)));
      if (linearProgress < 1) frameId = requestAnimationFrame(advance);
    });
    return () => cancelAnimationFrame(frameId);
  }, [isMotionReduced, targetValue]);

  return (
    <SbText variant={variant} color={color}>
      {`${shownValue}${unit}`}
    </SbText>
  );
}
