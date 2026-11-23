package kr.suhsaechan.secbrief.advisory.service;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사 목록: 필터 + 커서 페이지 (한 건 더 읽어 다음 커서 판정, 건수 쿼리 없음), AI 상태와 무관하게 모두
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleQueryService {

    private final ArticleRepository articleRepository;

    public ArticlePageResponse findArticles(ArticleSearchCondition condition) {
        int pageSize = condition.toPageSize();
        ArticleCursor cursor = condition.cursor() == null || condition.cursor().isBlank()
                ? null
                : ArticleCursor.decode(condition.cursor());

        List<Article> articles = articleRepository.findBy(
                ArticleSpecifications.toSearchSpecification(condition, cursor),
                query -> query.sortBy(ArticleSpecifications.NEWEST_FIRST).limit(pageSize + 1).all());

        // pageSize 보다 많이 읽혔으면 다음 페이지 있음
        boolean hasNextPage = articles.size() > pageSize;
        List<Article> pageArticles = hasNextPage ? articles.subList(0, pageSize) : articles;
        String nextCursor = null;
        if (hasNextPage) {
            Article lastArticle = pageArticles.get(pageArticles.size() - 1);
            nextCursor = ArticleCursor.encode(lastArticle.getPublishedAt(), lastArticle.getId());
        }
        return new ArticlePageResponse(pageArticles.stream().map(ArticleResponse::from).toList(), nextCursor);
    }
}
