package kr.suhsaechan.secbrief.advisory.collector;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;

/**
 * 출처 수집기 공통 규약: 수집 방식(FeedFormat)마다 구현체 1개, 외부 호출 > 원본 항목 목록
 * 실패는 CustomException 으로 던짐 (호출부가 수집 기록에 남김)
 */
public interface ArticleCollector {

    /** 이 수집기가 맡는 수집 방식 (출처의 feedFormat 과 같은 값을 맡음) */
    FeedFormat supportedFeedFormat();

    /**
     * 출처 1곳 수집: since 이후 항목만 (null 이면 제한 없음, 날짜로 범위를 못 줄이는 RSS 는 피드 전체)
     */
    List<CollectedArticle> collect(Source source, Instant since);
}
