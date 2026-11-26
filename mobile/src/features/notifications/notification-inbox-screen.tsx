import { useLocalSearchParams } from 'expo-router';
import { useState } from 'react';

import type { InboxNotificationKind } from '@/data/models/app-notification';
import { SbScreen, SbScreenHeader, SbSegmentedControl } from '@/design';

import { goBackOrHome } from '../navigation/go-back-or-home';
import { NotificationInboxList } from './notification-inbox-list';
import { useUnseenNotificationDots } from './use-unseen-notification-dots';

const BACK_ACCESSIBILITY_LABEL = '뒤로';

/** 경로 segment 값 > 처음 보일 칸 (NOTICE 면 공지사항, 그 밖은 알림) */
export function toInitialInboxKind(segment: string | undefined): InboxNotificationKind {
  return segment === 'NOTICE' ? 'NOTICE' : 'ALERT';
}

/** 알림함 화면 (탭 밖): 머리 > 알림 | 공지사항 (안 본 글이 있는 칸에 점) > 고른 칸의 목록 */
export function NotificationInboxScreen() {
  const { segment } = useLocalSearchParams<{ segment?: string }>();
  const [selectedKind, setSelectedKind] = useState<InboxNotificationKind>(() => toInitialInboxKind(segment));
  const { hasUnseenAlert, hasUnseenNotice } = useUnseenNotificationDots();
  return (
    <SbScreen>
      <SbScreenHeader title="알림함" backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <SbSegmentedControl
        options={[
          { value: 'ALERT', label: '알림', hasDot: hasUnseenAlert },
          { value: 'NOTICE', label: '공지사항', hasDot: hasUnseenNotice },
        ]}
        selectedValue={selectedKind}
        onSelected={setSelectedKind}
      />
      {/* 칸마다 목록을 새로 그림 (새 글 기준을 그 칸을 연 순간으로) */}
      <NotificationInboxList key={selectedKind} kind={selectedKind} />
    </SbScreen>
  );
}
