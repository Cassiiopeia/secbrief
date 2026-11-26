import { toApiAbsoluteUrl } from '@/core/config/api-config';
import type { BriefingItem, BriefingTopic } from '@/data/models/briefing-item';
import type { SbCoverKind } from '@/design';

// 소식 주제 > 대표 커버 그림 (주제 바로가기, 통계 타일 전용. 기사 그림 대신 쓰지 않음)
const COVER_KIND_BY_TOPIC: Record<BriefingTopic, SbCoverKind> = {
  vulnerability: 'target',
  ransomware: 'lock',
  privacy: 'fingerprint',
  remoteAccess: 'terminal',
  database: 'cylinder',
  supplyChain: 'network',
  policy: 'shield',
  event: 'calendar',
  general: 'signal',
};

/** 소식 주제 > 대표 커버 그림 (주제 바로가기, 통계 타일) */
export function toTopicCoverKind(topic: BriefingTopic): SbCoverKind {
  return COVER_KIND_BY_TOPIC[topic];
}

/** 소식 > 대표 이미지 절대 주소 (서버가 이미지 경로를 안 줬으면 없음: 그림 자리 없이 글로) */
export function toArticleImageUrl(item: BriefingItem): string | undefined {
  return item.imagePath === undefined ? undefined : toApiAbsoluteUrl(item.imagePath);
}
