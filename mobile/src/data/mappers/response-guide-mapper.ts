import {
  RESPONSE_GUIDE_MATCH_REASONS,
  RESPONSE_GUIDE_SECTIONS,
  type ResponseGuideItem,
  type ResponseGuideSection,
} from '../models/response-guide';
import { isArticleApiResponse, toBriefingItem } from './api-response-mapper';

/**
 * 서버 대응 모아보기 응답 > 구역 목록 (모든 칸을 믿지 않고 읽음: 이 필드가 생기기 전 서버는 아예 안 줌)
 * 모르는 구역·이유, 기사 모양이 틀린 항목, 항목이 없는 구역은 버림. 구역은 화면 순서로 다시 정렬
 */
export function toResponseGuideSections(value: unknown): ResponseGuideSection[] {
  if (!Array.isArray(value)) return [];
  const sections = value.flatMap((sectionValue): ResponseGuideSection[] => {
    if (!isObject(sectionValue)) return [];
    const section = RESPONSE_GUIDE_SECTIONS.find((kind) => kind === sectionValue.section);
    const items = Array.isArray(sectionValue.items)
      ? sectionValue.items.map(toResponseGuideItem).filter((item): item is ResponseGuideItem => item !== undefined)
      : [];
    if (section === undefined || items.length === 0) return [];
    const totalCount = typeof sectionValue.totalCount === 'number' ? Math.max(sectionValue.totalCount, items.length) : items.length;
    return [{ section, totalCount, items }];
  });
  return [...sections].sort((left, right) => RESPONSE_GUIDE_SECTIONS.indexOf(left.section) - RESPONSE_GUIDE_SECTIONS.indexOf(right.section));
}

/** 서버 항목 1건 > 대응 모아보기 항목 (기사 모양이 틀리거나 모르는 이유면 없음, 빈 글자는 없음으로) */
export function toResponseGuideItem(value: unknown): ResponseGuideItem | undefined {
  if (!isObject(value) || !isArticleApiResponse(value.article)) return undefined;
  const matchReason = RESPONSE_GUIDE_MATCH_REASONS.find((reason) => reason === value.matchReason);
  if (matchReason === undefined) return undefined;
  const article = toBriefingItem(value.article);
  return {
    article,
    sourceName: toNonEmptyText(value.sourceName) ?? article.publisherName,
    matchReason,
    matchedCveId: toNonEmptyText(value.matchedCveId),
    actionDueDate: toNonEmptyText(value.actionDueDate),
  };
}

function toNonEmptyText(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
