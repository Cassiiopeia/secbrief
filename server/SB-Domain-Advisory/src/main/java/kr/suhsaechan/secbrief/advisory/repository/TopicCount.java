package kr.suhsaechan.secbrief.advisory.repository;

import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;

/**
 * 주제별 기사 건수 (주간 동향 집계 결과)
 */
public record TopicCount(ArticleTopic topic, long count) {
}
