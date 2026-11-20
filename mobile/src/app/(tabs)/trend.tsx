import { SbTabPageScaffold } from '@/design';

/** 동향 탭 라우트 (내용 준비 전: 머리 + 빈 상태) */
export default function TrendRoute() {
  return (
    <SbTabPageScaffold
      titleLines={['동향']}
      emptyTitle="준비 중이에요"
      emptyDescription="이번 주에 많이 나온 주제를 곧 정리해 드릴게요."
      emptyIconName="trending-up"
    />
  );
}
