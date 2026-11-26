package kr.suhsaechan.secbrief.advisory.summary;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;

/**
 * AI 요약 요청 1건: 제목, 지역, 종류, 요약 입력 (출처가 준 요약문·공개 설명, 기사 본문 페이지는 아님)
 */
public record ArticleSummaryRequest(String title, ArticleRegion region, ArticleKind kind, String sourceText) {
}
