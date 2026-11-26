import { useAlertLevelDashboard } from '../alert-level/use-alert-level-dashboard';
import { toAlertLevelView } from './alert-level-view';

/** 사이버위기 경보 띠 (오늘 탭 위쪽). 본문이 이 조회를 기다리므로 실패하면 다시 시도하지 않고 바로 띠 없이 보여 줌 */
export function useKisaAlertLevel() {
  return useAlertLevelDashboard((dashboard) => toAlertLevelView(dashboard) ?? null, false);
}
