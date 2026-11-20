import { SbTabPageScaffold } from '@/design';

/** 뉴스 탭 라우트 (내용 준비 전: 머리 + 빈 상태) */
export default function NewsRoute() {
  return (
    <SbTabPageScaffold
      titleLines={['뉴스']}
      emptyTitle="준비 중이에요"
      emptyDescription="국내외 보안 소식을 곧 여기에서 볼 수 있어요."
      emptyIconName="article"
    />
  );
}
