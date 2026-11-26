package kr.suhsaechan.secbrief.advisory.collector;

import java.util.List;

/**
 * KISA 게시판 목록 1쪽: 맨 위 고정 글(공지, 쪽마다 반복), 일반 글(최신순), 다음 쪽이 있는지
 */
public record KisaBoardPage(List<CollectedArticle> pinnedArticles, List<CollectedArticle> regularArticles,
                            boolean hasNextPage) {

    public KisaBoardPage {
        pinnedArticles = List.copyOf(pinnedArticles);
        regularArticles = List.copyOf(regularArticles);
    }
}
