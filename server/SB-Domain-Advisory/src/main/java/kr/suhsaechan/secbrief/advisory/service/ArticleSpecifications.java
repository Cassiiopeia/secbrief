package kr.suhsaechan.secbrief.advisory.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 기사 조회 조건: 종류·지역·주제 필터, 커서 이전, 긴급, 게시일 이후 + 최신순 정렬
 */
public final class ArticleSpecifications {

    // 최신순 (같은 게시일은 id 큰 것 먼저: 커서 비교와 같은 순서)
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private ArticleSpecifications() {
    }

    /**
     * 목록 조건: null 아닌 필터만 + 커서가 있으면 그 이전
     */
    public static Specification<Article> toSearchSpecification(ArticleSearchCondition condition, ArticleCursor cursor) {
        List<Specification<Article>> specifications = new ArrayList<>();
        if (condition.kind() != null) {
            specifications.add(hasKind(condition.kind()));
        }
        if (condition.region() != null) {
            specifications.add(hasRegion(condition.region()));
        }
        if (condition.topic() != null) {
            specifications.add(hasTopic(condition.topic()));
        }
        if (cursor != null) {
            specifications.add(isBeforeCursor(cursor));
        }
        return Specification.allOf(specifications);
    }

    public static Specification<Article> hasKind(ArticleKind kind) {
        return (root, query, builder) -> builder.equal(root.get("kind"), kind);
    }

    public static Specification<Article> hasRegion(ArticleRegion region) {
        return (root, query, builder) -> builder.equal(root.get("region"), region);
    }

    public static Specification<Article> hasTopic(ArticleTopic topic) {
        return (root, query, builder) -> builder.equal(root.get("topic"), topic);
    }

    /**
     * 커서 이전: publishedAt < c.publishedAt or (publishedAt = c.publishedAt and id < c.id)
     */
    public static Specification<Article> isBeforeCursor(ArticleCursor cursor) {
        return (root, query, builder) -> builder.or(
                builder.lessThan(root.get("publishedAt"), cursor.publishedAt()),
                builder.and(
                        builder.equal(root.get("publishedAt"), cursor.publishedAt()),
                        builder.lessThan(root.get("id"), cursor.id())));
    }

    public static Specification<Article> isUrgentPublishedSince(Instant start) {
        return (root, query, builder) -> builder.and(
                builder.isTrue(root.get("isUrgent")),
                builder.greaterThanOrEqualTo(root.get("publishedAt"), start));
    }

    public static Specification<Article> hasIdOtherThan(Long id) {
        return (root, query, builder) -> builder.notEqual(root.get("id"), id);
    }
}
