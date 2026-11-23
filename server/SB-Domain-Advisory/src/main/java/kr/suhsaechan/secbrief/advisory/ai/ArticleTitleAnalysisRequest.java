package kr.suhsaechan.secbrief.advisory.ai;

import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;

/**
 * AI 분석 요청: 제목과 지역만 (본문은 보내지 않음)
 */
public record ArticleTitleAnalysisRequest(String title, ArticleRegion region) {
}
