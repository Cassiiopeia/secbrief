import { SbText } from './sb-text';

interface SbCapsLabelProps {
  text: string;
  color: string;
}

/** 대문자 고정폭 라벨 (예: 긴급 / CVSS 9.8). 영문은 대문자로 바꿔 보여 준다 */
export function SbCapsLabel({ text, color }: SbCapsLabelProps) {
  return (
    <SbText variant="capsLabel" color={color}>
      {text.toUpperCase()}
    </SbText>
  );
}
