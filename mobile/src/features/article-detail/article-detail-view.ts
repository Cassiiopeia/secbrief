import type { ArticleDetail } from '@/data/models/article-detail';
import type { BriefingTopic } from '@/data/models/briefing-item';
import type { SbFact } from '@/design';

import { type ArticleRowView, toArticleRowView, toTopicLabel } from '../articles/article-row-view';
import { BROWSABLE_TOPICS, toTopicBrowseKey } from '../browse/browse-target';

/** 함께 볼 소식이 없을 때 대신 보여 주는 이동: 버튼 문구 + 모아 보기 묶음 키 (없으면 홈) */
export interface MoreArticlesLink {
  label: string;
  browseKey?: string;
}

/** 기사 상세 화면 표시 내용 (요약·핵심·대응·확인된 정보·함께 볼 소식) */
export interface ArticleDetailView {
  article: ArticleRowView;
  /** 요약 문단 (없으면 준비 중 안내) */
  summary?: string;
  keyPoints: string[];
  actionGuide?: string;
  /** 요약·핵심·대응 카드 제목 옆 작은 표시 (AI 가 쓴 경우만) */
  aiNoticeLabel?: string;
  /** 요약도 핵심도 아직 없음 */
  isSummaryPending: boolean;
  /** 서버가 준 값 그대로 (앱에서 만들거나 고치지 않음) */
  facts: SbFact[];
  relatedArticles: ArticleRowView[];
  /** 함께 볼 소식이 한 건도 없을 때만 있음 */
  moreArticlesLink?: MoreArticlesLink;
}

const AI_SUMMARY_NOTICE_LABEL = 'AI 가 요약했어요';

/** 기사 상세 > 표시 내용: 기사 표시 내용 > 요약 > 핵심 > 대응 > 확인된 정보 > 함께 볼 소식(자기 자신 제외, 없으면 다른 소식 이동) */
export function toArticleDetailView(detail: ArticleDetail, now: Date): ArticleDetailView {
  const keyPoints = detail.keyPoints.map((keyPoint) => keyPoint.trim()).filter((keyPoint) => keyPoint !== '');
  const relatedArticles = detail.relatedItems
    .filter((relatedItem) => relatedItem.id !== detail.article.id)
    .map((relatedItem) => toArticleRowView(relatedItem, now));
  return {
    article: toArticleRowView(detail.article, now),
    summary: detail.summary,
    keyPoints,
    actionGuide: detail.actionGuide,
    aiNoticeLabel: detail.isAiSummarized ? AI_SUMMARY_NOTICE_LABEL : undefined,
    isSummaryPending: detail.summary === undefined && keyPoints.length === 0,
    facts: detail.facts.filter((fact) => fact.label.trim() !== '' && fact.value.trim() !== ''),
    relatedArticles,
    moreArticlesLink: relatedArticles.length === 0 ? toMoreArticlesLink(detail.article.topic) : undefined,
  };
}

/** 주제 > 다른 소식 이동: 모아 볼 수 있는 주제면 그 주제 모아 보기, 일반이면 홈 */
function toMoreArticlesLink(topic: BriefingTopic): MoreArticlesLink {
  if (!BROWSABLE_TOPICS.includes(topic)) return { label: '다른 소식 보러 가기' };
  return { label: `다른 ${toTopicLabel(topic)} 소식 보러 가기`, browseKey: toTopicBrowseKey(topic) };
}

/** 원문 보기 버튼 문구 (게시자 이름과 함께) */
export function toOriginalButtonLabel(article: ArticleRowView): string {
  return `${article.publisherName} 원문 보기`;
}

/** 공유할 글: 제목 + 원문 링크 (요약은 넣지 않음) */
export function toShareMessage(article: ArticleRowView): string {
  return `${article.title}\n${article.originalUrl}`;
}
