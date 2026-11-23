import { toArticleDateLabel } from '@/core/format/briefing-date-formatter';
import type { CollectSourceStatus } from '@/data/models/collect-source-status';

import { toKindLabel, toRegionLabel } from '../articles/article-row-view';

/** 출처 행 표시 내용 */
export interface SourceStatusRowView {
  code: string;
  title: string;
  detail: string;
}

/** 출처 상태 > 행: 지역 · 종류 · 마지막 갱신 (아직 못 모음 / 마지막 시도 실패 안내) */
export function toSourceStatusRowView(status: CollectSourceStatus, now: Date): SourceStatusRowView {
  const prefix = `${toRegionLabel(status.region)} · ${toKindLabel(status.kind)}`;
  if (status.lastCollectedAt === undefined) {
    return { code: status.code, title: status.displayName, detail: `${prefix} · 아직 모으는 중이에요` };
  }
  const lastCollectedLabel = `마지막 갱신 ${toArticleDateLabel(status.lastCollectedAt, now)}`;
  const failureSuffix = status.isLastCollectSucceeded ? '' : ' · 다음 주기에 다시 모아요';
  return { code: status.code, title: status.displayName, detail: `${prefix} · ${lastCollectedLabel}${failureSuffix}` };
}
