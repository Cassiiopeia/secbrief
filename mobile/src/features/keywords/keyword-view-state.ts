import type { TrendingKeyword } from '@/data/models/trending-keyword';
import type { SbHashtag, SbRatioChange } from '@/design';

// 카드에 보일 해시태그 수 한도
const CARD_HASHTAG_MAXIMUM_COUNT = 3;
const HASH_SIGN = '#';
const NEW_RANK_LABEL = 'NEW';

/** 키워드 순위 한 줄 (탐색 탭 막대) */
export interface KeywordRankView {
  keyword: string;
  /** 예: 1 */
  rankLabel: string;
  /** 예: #랜섬웨어 */
  title: string;
  /** 예: 12건 */
  countLabel: string;
  /** 1위 대비 비율 (0~1) */
  ratio: number;
  /** 순위 변화 표시 (서버가 변화를 안 주면 없음) */
  change?: SbRatioChange;
  accessibilityLabel: string;
}

/** 소식의 키워드 > 카드 해시태그 (앞에서 3개까지, 빈 값·중복 제외) */
export function toCardHashtags(keywords: string[]): SbHashtag[] {
  const uniqueKeywords = [...new Set(keywords.map((keyword) => keyword.trim()).filter((keyword) => keyword !== ''))];
  return uniqueKeywords.slice(0, CARD_HASHTAG_MAXIMUM_COUNT).map((keyword) => ({ keyword }));
}

/** 많이 나온 키워드 > 칩 구름 (건수 포함, 0건·빈 키워드 제외) */
export function toTrendingHashtags(trendingKeywords: TrendingKeyword[]): SbHashtag[] {
  return toShownKeywords(trendingKeywords).map(({ keyword, articleCount }) => ({ keyword, countLabel: `${articleCount}건` }));
}

/** 많이 나온 키워드 > 순위 줄 (위에서 maximumCount 개, 막대는 1위 대비) */
export function toKeywordRankViews(trendingKeywords: TrendingKeyword[], maximumCount: number): KeywordRankView[] {
  const shownKeywords = toShownKeywords(trendingKeywords).slice(0, maximumCount);
  const topArticleCount = Math.max(...shownKeywords.map(({ articleCount }) => articleCount), 0);
  return shownKeywords.map((trendingKeyword) => {
    const { keyword, articleCount, rank } = trendingKeyword;
    const change = toRankChange(trendingKeyword);
    const changePart = change === undefined ? '' : `, ${change.accessibilityLabel}`;
    return {
      keyword,
      rankLabel: String(rank),
      title: `${HASH_SIGN}${keyword}`,
      countLabel: `${articleCount}건`,
      ratio: topArticleCount > 0 ? articleCount / topArticleCount : 0,
      change,
      accessibilityLabel: `${rank}위, ${keyword}, ${articleCount}건${changePart}, 이 키워드 소식 보기`,
    };
  });
}

/** 순위 변화 > 표시: 신규 NEW, 상승·하락은 화살표 + 칸 수, 유지는 줄표, 변화를 모르면 없음 */
export function toRankChange(trendingKeyword: TrendingKeyword): (SbRatioChange & { accessibilityLabel: string }) | undefined {
  if (trendingKeyword.isNew) return { direction: 'new', label: NEW_RANK_LABEL, accessibilityLabel: '새로 올라옴' };
  const { rankChange } = trendingKeyword;
  if (rankChange === undefined) return undefined;
  if (rankChange > 0) return { direction: 'up', label: String(rankChange), accessibilityLabel: `${rankChange}계단 상승` };
  if (rankChange < 0) return { direction: 'down', label: String(-rankChange), accessibilityLabel: `${-rankChange}계단 하락` };
  return { direction: 'flat', label: '', accessibilityLabel: '순위 유지' };
}

/** 해시태그 읽기 도우미 문구 */
export function toHashtagAccessibilityLabel(hashtag: SbHashtag): string {
  const countPart = hashtag.countLabel === undefined ? '' : `, ${hashtag.countLabel}`;
  return `키워드 ${hashtag.keyword}${countPart}, 이 키워드 소식 보기`;
}

function toShownKeywords(trendingKeywords: TrendingKeyword[]): TrendingKeyword[] {
  return trendingKeywords.filter(({ keyword, articleCount }) => keyword.trim() !== '' && articleCount > 0);
}
