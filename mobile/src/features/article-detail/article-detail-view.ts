import { toSharedArticleUrl } from '@/core/config/share-link-config';
import type { ArticleDetail } from '@/data/models/article-detail';
import type { BriefingTopic } from '@/data/models/briefing-item';
import type { SbFact } from '@/design';

import { type ArticleRowView, toArticleRowView, toTopicLabel } from '../articles/article-row-view';
import { BROWSABLE_TOPICS, toTopicBrowseKey } from '../browse/browse-target';
import { type ResponseGuideSectionView, toResponseGuideSectionViews } from '../response-guide/response-guide-view';
import { AI_SUMMARY_NOTICE_LABEL, type ArticleBriefingView, toArticleBriefingView } from './article-briefing-view';

/** 함께 볼 소식이 없을 때 대신 보여 주는 이동: 버튼 문구 + 모아 보기 묶음 키 (없으면 홈) */
export interface MoreArticlesLink {
  label: string;
  browseKey?: string;
}

/**
 * 기사 상세 화면 표시 내용: AI 브리핑(있으면 이것만) 또는 이전 방식 요약·핵심·대응 + 확인된 정보 + 대응 모아보기(없으면 함께 볼 소식)
 */
export interface ArticleDetailView {
  article: ArticleRowView;
  /** AI 브리핑 (있으면 요약·핵심·대응 카드 대신 이것을 보여 줌) */
  briefing?: ArticleBriefingView;
  /** 요약 문단 (없으면 요약 없음 안내) */
  summary?: string;
  keyPoints: string[];
  actionGuide?: string;
  /** 요약·핵심·대응 카드 제목 옆 작은 표시 (AI 가 쓴 경우만) */
  aiNoticeLabel?: string;
  /** 요약도 핵심도 없음 (입력 없음·대기·실패 공통) */
  isSummaryMissing: boolean;
  /** 요약이 없고 서버가 대기 중이라고 함: 요약 카드 자리에 준비 중 안내 (입력 없음·실패는 카드 숨김) */
  isSummaryPending: boolean;
  /** 서버가 준 값 그대로 (앱에서 만들거나 고치지 않음) */
  facts: SbFact[];
  /** 대응 모아보기 구역 (공식 대응 > 확인된 취약점 정보 > 관련 기사, 없으면 빈 배열) */
  responseGuideSections: ResponseGuideSectionView[];
  /** 대응 모아보기가 비었을 때만 채움 (있으면 빈 배열: 같은 자료를 두 번 보여 주지 않음) */
  relatedArticles: ArticleRowView[];
  /** 대응 모아보기도 함께 볼 소식도 없을 때만 있음 */
  moreArticlesLink?: MoreArticlesLink;
}

/**
 * 기사 상세 > 표시 내용: 기사 표시 내용 > 브리핑 > 요약 > 핵심 > 대응 > 확인된 정보
 * > 대응 모아보기 (비었으면 함께 볼 소식(자기 자신 제외), 그것도 없으면 다른 소식 이동)
 */
export function toArticleDetailView(detail: ArticleDetail, now: Date): ArticleDetailView {
  const keyPoints = detail.keyPoints.map((keyPoint) => keyPoint.trim()).filter((keyPoint) => keyPoint !== '');
  const isSummaryMissing = detail.summary === undefined && keyPoints.length === 0;
  const responseGuideSections = toResponseGuideSectionViews(detail.responseGuideSections, now);
  const relatedArticles =
    responseGuideSections.length > 0
      ? []
      : detail.relatedItems.filter((relatedItem) => relatedItem.id !== detail.article.id).map((relatedItem) => toArticleRowView(relatedItem, now));
  return {
    article: toArticleRowView(detail.article, now),
    briefing: detail.brief && toArticleBriefingView(detail.brief),
    summary: detail.summary,
    keyPoints,
    actionGuide: detail.actionGuide,
    aiNoticeLabel: detail.isAiSummarized ? AI_SUMMARY_NOTICE_LABEL : undefined,
    isSummaryMissing,
    isSummaryPending: isSummaryMissing && detail.brief === undefined && detail.isSummaryPending,
    facts: detail.facts.filter((fact) => fact.label.trim() !== '' && fact.value.trim() !== ''),
    responseGuideSections,
    relatedArticles,
    moreArticlesLink:
      responseGuideSections.length === 0 && relatedArticles.length === 0 ? toMoreArticlesLink(detail.article.topic) : undefined,
  };
}

/** 주제 > 다른 소식 이동: 모아 볼 수 있는 주제면 그 주제 모아 보기, 일반이면 홈 */
function toMoreArticlesLink(topic: BriefingTopic): MoreArticlesLink {
  if (!BROWSABLE_TOPICS.includes(topic)) return { label: '다른 소식 보러 가기' };
  return { label: `다른 ${toTopicLabel(topic)} 소식 보러 가기`, browseKey: toTopicBrowseKey(topic) };
}

// 원문 보기 버튼 (#112): 링크 복사·공유 아이콘 옆 남은 폭에 게시자 이름이 들어가는지 폭 단위로 어림
const ORIGINAL_BUTTON_SUFFIX = '원문 보기';
// 게시자 이름 최대 폭 (반각 1 · 전각 2 단위): 360pt 폭·글자 배율 1배 웹 실측 > 글자 칸 150px, " 원문 보기" 55px
// 13 단위 "KISA 경보단계" 138px 들어감, 15 단위 "GitHub 보안 권고" 156px 잘림
const MAXIMUM_PUBLISHER_NAME_WIDTH_UNITS_IN_ORIGINAL_BUTTON = 13;
// 전각 글자: 한글 자모·음절, CJK 기호·한자, 전각 영숫자
const FULL_WIDTH_CHARACTER_PATTERN = /[\u1100-\u11FF\u3000-\u9FFF\uAC00-\uD7AF\uFF00-\uFFEF]/;
const FULL_WIDTH_CHARACTER_UNITS = 2;
const HALF_WIDTH_CHARACTER_UNITS = 1;

/** 원문 보기 버튼 문구: 게시자 이름이 버튼에 들어가면 "{게시자} 원문 보기", 길면 "원문 보기" */
export function toOriginalButtonLabel(article: ArticleRowView): string {
  return isPublisherNameFittingOriginalButton(article.publisherName) ? toOriginalButtonAccessibilityLabel(article) : ORIGINAL_BUTTON_SUFFIX;
}

/** 원문 보기 버튼 읽기 도우미 문구: 버튼 문구가 줄어도 항상 "{게시자} 원문 보기" */
export function toOriginalButtonAccessibilityLabel(article: ArticleRowView): string {
  return `${article.publisherName} ${ORIGINAL_BUTTON_SUFFIX}`;
}

function isPublisherNameFittingOriginalButton(publisherName: string): boolean {
  return toTextWidthUnits(publisherName) <= MAXIMUM_PUBLISHER_NAME_WIDTH_UNITS_IN_ORIGINAL_BUTTON;
}

// 글자 폭 어림 (전각 2, 그 밖 1)
function toTextWidthUnits(text: string): number {
  return Array.from(text).reduce(
    (widthUnits, character) =>
      widthUnits + (FULL_WIDTH_CHARACTER_PATTERN.test(character) ? FULL_WIDTH_CHARACTER_UNITS : HALF_WIDTH_CHARACTER_UNITS),
    0,
  );
}

/** 공유 내용 (Share.share 첫 인자): iOS 는 url 칸이 따로 있음, Android 는 url 을 무시해 글에 이어 붙임 */
export interface ArticleShareContent {
  message: string;
  url?: string;
}

/** 공유 내용: 한국어 제목 + 시큐브리프 공유 링크 (원문 링크·요약은 넣지 않음, 원문은 링크 복사로) */
export function toArticleShareContent(article: ArticleRowView, platformOs: string): ArticleShareContent {
  const sharedArticleUrl = toSharedArticleUrl(article.id);
  return platformOs === 'ios' ? { message: article.title, url: sharedArticleUrl } : { message: `${article.title}\n${sharedArticleUrl}` };
}
