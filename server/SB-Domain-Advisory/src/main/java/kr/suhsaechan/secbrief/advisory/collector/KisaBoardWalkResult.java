package kr.suhsaechan.secbrief.advisory.collector;

import java.util.List;

/**
 * KISA 게시판 쪽 넘김 결과: 범위 안 글(최신순, 중복 없음), 읽은 쪽 수, 쪽 수 상한에서 멈췄는지
 */
public record KisaBoardWalkResult(List<CollectedArticle> articles, int readPageCount, boolean isPageLimitReached) {

    public KisaBoardWalkResult {
        articles = List.copyOf(articles);
    }
}
