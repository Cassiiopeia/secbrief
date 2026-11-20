import { SbTabPageScaffold } from '@/design';

/** 설정 탭 라우트 (내용 준비 전: 머리 + 빈 상태) */
export default function SettingsRoute() {
  return (
    <SbTabPageScaffold
      titleLines={['설정']}
      emptyTitle="준비 중이에요"
      emptyDescription="내 스택과 알림 기준을 곧 고를 수 있어요."
      emptyIconName="tune"
    />
  );
}
