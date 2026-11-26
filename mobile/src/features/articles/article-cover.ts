import { toApiAbsoluteUrl } from '@/core/config/api-config';
import type { BriefingItem, BriefingTopic } from '@/data/models/briefing-item';
import type { SbArticleThumbnail, SbCoverKind, SbCoverTone } from '@/design';

// 소식 주제 > 커버 그림 후보 (첫째가 주제 대표 그림). 후보가 여럿이면 기사마다 섞어 씀
const COVER_KINDS_BY_TOPIC: Record<BriefingTopic, SbCoverKind[]> = {
  vulnerability: ['target', 'shield'],
  ransomware: ['lock'],
  privacy: ['fingerprint'],
  remoteAccess: ['terminal'],
  database: ['cylinder'],
  supplyChain: ['network'],
  policy: ['shield'],
  event: ['calendar'],
  general: ['signal', 'network', 'shield'],
};
// 큰 카드 그림 배색 후보 (기사마다 섞어 씀)
const COVER_TONES: SbCoverTone[] = ['feature', 'accent', 'ink', 'neutral'];

/** 소식 주제 > 대표 커버 그림 (주제 바로가기, 통계 타일) */
export function toTopicCoverKind(topic: BriefingTopic): SbCoverKind {
  return COVER_KINDS_BY_TOPIC[topic][0];
}

// 기사 id > 순번 (글자 코드 합: 숫자가 아닌 id 도 같은 방식)
function toCoverSequence(articleId: string): number {
  return Array.from(articleId).reduce((sum, character) => sum + character.charCodeAt(0), 0);
}

/** 소식 > 커버 그림 종류: 주제의 후보 중 하나 (같은 기사는 늘 같은 그림) */
export function toArticleCoverKind(item: BriefingItem): SbCoverKind {
  const coverKinds = COVER_KINDS_BY_TOPIC[item.topic];
  const coverSequence = toCoverSequence(item.id);
  // 배색이 한 바퀴 돌 때마다 한 칸 더 밀어 그림·배색 조합이 고르게 나오게
  return coverKinds[(coverSequence + Math.floor(coverSequence / COVER_TONES.length)) % coverKinds.length];
}

/** 소식 > 큰 카드 그림 배색 (같은 기사는 늘 같은 배색) */
function toArticleCoverTone(item: BriefingItem): SbCoverTone {
  return COVER_TONES[toCoverSequence(item.id) % COVER_TONES.length];
}

/** 소식 > 대표 이미지 절대 주소 (서버가 이미지 경로를 안 줬으면 없음) */
export function toArticleImageUrl(item: BriefingItem): string | undefined {
  return item.imagePath === undefined ? undefined : toApiAbsoluteUrl(item.imagePath);
}

/** 소식 > 큰 카드 그림 (항상 있음): 대표 이미지 + 대신 보일 커버 그림 (주제 + 기사 id 로 고른 그림·배색) */
export function toArticleCover(item: BriefingItem): SbArticleThumbnail {
  return { imageUrl: toArticleImageUrl(item), coverKind: toArticleCoverKind(item), coverTone: toArticleCoverTone(item) };
}

/**
 * 소식 > 목록 행 썸네일: 뉴스이거나 대표 이미지가 있을 때만 (공지·취약점 목록은 글만)
 * 이미지가 없거나 못 불러오면 커버 그림이 같은 자리에 보임 (작은 자리라 배색은 회색 하나)
 */
export function toArticleRowThumbnail(item: BriefingItem): SbArticleThumbnail | undefined {
  const imageUrl = toArticleImageUrl(item);
  if (item.kind !== 'news' && imageUrl === undefined) return undefined;
  return { imageUrl, coverKind: toArticleCoverKind(item), coverTone: 'neutral' };
}
