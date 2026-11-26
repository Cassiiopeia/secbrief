import { SbMasthead } from './sb-masthead';
import { SbScreen } from './sb-screen';
import type { SbStateIllustrationKind } from './sb-state-illustration-sources';
import { SbEmptyState } from './sb-state-views';

interface SbTabPageScaffoldProps {
  titleLines: string[];
  emptyTitle: string;
  emptyDescription: string;
  emptyIllustration?: SbStateIllustrationKind;
}

/** 아직 내용이 없는 탭 화면 틀: 머리 + 빈 상태 */
export function SbTabPageScaffold({ titleLines, emptyTitle, emptyDescription, emptyIllustration }: SbTabPageScaffoldProps) {
  return (
    <SbScreen>
      <SbMasthead titleLines={titleLines} />
      <SbEmptyState title={emptyTitle} description={emptyDescription} illustration={emptyIllustration} />
    </SbScreen>
  );
}
