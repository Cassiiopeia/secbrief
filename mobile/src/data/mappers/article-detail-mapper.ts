import type { ArticleDetail } from '../models/article-detail';
import { type ArticleApiResponse, toBriefingItem } from './api-response-mapper';
import { type ArticleBriefApiResponse, toArticleBrief } from './article-brief-mapper';
import { toResponseGuideSections } from './response-guide-mapper';

// 기사 상세 응답 > 상세: 기사 매퍼·대응 모아보기 매퍼를 함께 써서 따로 둠 (두 매퍼 순환 import 방지, #87)

export interface ArticleDetailApiResponse {
  article: ArticleApiResponse;
  summary?: string | null;
  keyPoints?: string[] | null;
  actionGuide?: string | null;
  isAiSummarized?: boolean | null;
  /** 요약 대기 중 (#87). 이 필드가 생기기 전 서버는 아예 안 줌 */
  isSummaryPending?: boolean | null;
  /** AI 브리핑. 이전 방식 요약만 있으면 null, 이 필드가 생기기 전 서버는 아예 안 줌 */
  brief?: ArticleBriefApiResponse | null;
  facts?: { label: string; value: string }[] | null;
  relatedItems?: ArticleApiResponse[] | null;
  /** 대응 모아보기 (#71). 이 필드가 생기기 전 서버는 아예 안 줌 (모양은 response-guide-mapper 가 확인) */
  responseGuideSections?: unknown;
}

/** 기사 상세 응답 > 상세 (요약이 아직 없으면 summary 없음·keyPoints 빈 배열, 없는 목록은 빈 배열, 브리핑·대응 모아보기는 읽을 수 있을 때만) */
export function toArticleDetail(response: ArticleDetailApiResponse): ArticleDetail {
  const article = toBriefingItem(response.article);
  return {
    article,
    brief: toArticleBrief(response.brief, article.oneLine),
    summary: response.summary ? response.summary : undefined,
    keyPoints: response.keyPoints ?? [],
    actionGuide: response.actionGuide ? response.actionGuide : undefined,
    isAiSummarized: response.isAiSummarized === true,
    isSummaryPending: response.isSummaryPending === true,
    facts: (response.facts ?? []).map(({ label, value }) => ({ label, value })),
    relatedItems: (response.relatedItems ?? []).map(toBriefingItem),
    responseGuideSections: toResponseGuideSections(response.responseGuideSections),
  };
}
