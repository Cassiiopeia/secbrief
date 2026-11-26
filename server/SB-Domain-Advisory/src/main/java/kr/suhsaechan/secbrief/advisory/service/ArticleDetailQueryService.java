package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.RelatedArticlePolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사 상세(브리프): 기사 1건 > 관련 기사 ID(순위 조회 1회) > 관련 기사 묶음 조회 > 대응 모아보기 > 응답
 * 없는 id 는 RESOURCE_NOT_FOUND, 요약 상태와 무관하게 열림 (요약이 없으면 제목·사실 값·원문 링크)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleDetailQueryService {

    private final ArticleRepository articleRepository;
    private final ResponseGuideQueryService responseGuideQueryService;
    private final Clock clock;

    public ArticleDetailResponse findArticleDetail(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return ArticleDetailResponse.of(article, findRelatedArticles(article),
                responseGuideQueryService.findResponseGuideSections(article));
    }

    // 관련 순위대로 ID 를 받은 뒤 한 번에 읽고, 받은 ID 순서로 다시 정렬 (IN 조회는 순서를 지키지 않음)
    private List<Article> findRelatedArticles(Article article) {
        List<Long> relatedArticleIds = articleRepository.findRelatedArticleIds(
                article.getId(),
                RelatedArticlePolicy.toCveIdPattern(article.getCveId(), article.getTitle()),
                article.getSubjectName() == null ? "" : article.getSubjectName(),
                article.getTopic().name(),
                clock.instant().minus(RelatedArticlePolicy.RELATED_ARTICLE_LOOKBACK),
                RelatedArticlePolicy.MAXIMUM_RELATED_ARTICLE_COUNT);
        if (relatedArticleIds.isEmpty()) {
            return List.of();
        }
        return articleRepository.findAllById(relatedArticleIds).stream()
                .sorted(Comparator.comparingInt(relatedArticle -> relatedArticleIds.indexOf(relatedArticle.getId())))
                .toList();
    }
}
