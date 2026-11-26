import { CISA_KEV_SOURCE_CODE } from '@/data/models/article-source-code';
import type { ArticleFilter, ArticleImportance, BriefingItem } from '@/data/models/briefing-item';

// 중요도 순위 (클수록 중요)
const IMPORTANCE_RANK: Record<ArticleImportance, number> = { routine: 0, notable: 1, headline: 2 };

/**
 * 소식이 필터 조건 8개(중요도 하한, 출처, 출처 여러 곳, 키워드, 긴급만, 분야, 생태계, 주요)에 맞는지
 * 서버가 이 조건을 모르는 이전 버전이면 조건을 무시하고 전부 돌려주므로, 엉뚱한 소식이 섞이지 않게 앱에서 한 번 더 거름
 */
export function isArticleMatchingFilter(item: BriefingItem, filter: ArticleFilter): boolean {
  if (filter.minimumImportance !== undefined && IMPORTANCE_RANK[item.importance] < IMPORTANCE_RANK[filter.minimumImportance]) {
    return false;
  }
  if (filter.sourceCode !== undefined && !isFromSource(item, filter.sourceCode)) return false;
  if (filter.keyword !== undefined && !item.keywords.includes(filter.keyword)) return false;
  if (filter.sourceCodes !== undefined && filter.sourceCodes.length > 0 && !filter.sourceCodes.some((sourceCode) => isFromSource(item, sourceCode))) {
    return false;
  }
  if (filter.fieldCode !== undefined && item.fieldCode !== filter.fieldCode) return false;
  if (filter.ecosystemCode !== undefined && item.ecosystemCode !== filter.ecosystemCode) return false;
  if (filter.isNotable !== undefined && item.isNotable !== filter.isNotable) return false;
  if (filter.isUrgent === true && !item.isUrgent) return false;
  return true;
}

// 출처 코드가 없는 응답(이전 서버)은 KEV 만 등재 여부로 대신 판단, 그 밖의 출처는 알 수 없어 뺌
function isFromSource(item: BriefingItem, sourceCode: string): boolean {
  if (item.sourceCode !== undefined) return item.sourceCode === sourceCode;
  return sourceCode === CISA_KEV_SOURCE_CODE && item.isKev;
}
