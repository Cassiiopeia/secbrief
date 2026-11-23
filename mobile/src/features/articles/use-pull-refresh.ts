import { useState } from 'react';

/** 당겨서 새로고침 상태: 사용자가 당겨서 시작한 새로고침만 표시 (자동 재조회 때는 돌지 않게) */
export function usePullRefresh(refetch: () => Promise<unknown>) {
  const [isPullRefreshing, setIsPullRefreshing] = useState(false);

  const onPullRefresh = async () => {
    setIsPullRefreshing(true);
    try {
      await refetch();
    } finally {
      setIsPullRefreshing(false); // (성공·실패 공통)
    }
  };

  return { isPullRefreshing, onPullRefresh };
}
