package kr.suhsaechan.secbrief.advisory.ai;

import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;

/**
 * AI 분석 결과: 주제 1개 + 한국어 제목 (국내 항목·번역 없음이면 null, 검증 전 값)
 */
public record ArticleTitleAnalysis(ArticleTopic topic, String translatedTitle) {
}
