package kr.suhsaechan.secbrief.advisory.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.ArticleTagFilter;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 기사 조회 조건: 종류·지역·주제·최소 중요도·출처·키워드·분류(#93) 필터, 검색어 포함, 커서 이전, 긴급, 게시일 이후 + 최신순·중요도순 정렬
 */
public final class ArticleSpecifications {

    // Article 의 분류 묶음 (ArticleClassificationState) 경로
    private static final String CLASSIFICATION_STATE = "classificationState";

    // 최신순 (같은 게시일은 id 큰 것 먼저: 커서 비교와 같은 순서)
    public static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    // 중요도 높은 순 > 최신순 (importance 열은 이름 문자열: HEADLINE < NOTABLE < ROUTINE 글자순이 곧 높은 순)
    public static final Sort MOST_IMPORTANT_FIRST = Sort.by(Sort.Order.asc("importance")).and(NEWEST_FIRST);

    // LIKE 이스케이프 문자: 검색어의 %, _, \ 를 글자 그대로 찾게 함
    private static final char LIKE_ESCAPE_CHARACTER = '\\';
    private static final String LIKE_WILDCARD = "%";

    private ArticleSpecifications() {
    }

    /**
     * 목록 조건: 값이 있는 필터만 (null, 출처·키워드는 빈 문자열도 제외) + 커서가 있으면 그 이전
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
        if (condition.minimumImportance() != null) {
            specifications.add(hasImportanceAtLeast(condition.minimumImportance()));
        }
        if (condition.sourceCode() != null && !condition.sourceCode().isBlank()) {
            specifications.add(hasSourceCode(condition.sourceCode()));
        }
        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            specifications.add(hasKeyword(condition.keyword()));
        }
        if (Boolean.TRUE.equals(condition.isUrgent())) {
            specifications.add(isUrgent());
        }
        specifications.addAll(toTagSpecifications(condition.tagFilter()));
        if (cursor != null) {
            specifications.add(isBeforeCursor(cursor));
        }
        return Specification.allOf(specifications);
    }

    /**
     * 분류 조건 (#93): 출처 여러 곳, 분야, 생태계, 주요 여부, 내 기술(셋 중 하나), 수집 시각 이후
     */
    public static List<Specification<Article>> toTagSpecifications(ArticleTagFilter tagFilter) {
        List<Specification<Article>> specifications = new ArrayList<>();
        if (!tagFilter.sourceCodes().isEmpty()) {
            specifications.add((root, query, builder) -> root.get("source").get("code").in(tagFilter.sourceCodes()));
        }
        if (tagFilter.fieldCode() != null && !tagFilter.fieldCode().isBlank()) {
            specifications.add((root, query, builder) -> builder.equal(root.get(CLASSIFICATION_STATE).get("fieldCode"), tagFilter.fieldCode()));
        }
        if (tagFilter.ecosystemCode() != null && !tagFilter.ecosystemCode().isBlank()) {
            specifications.add((root, query, builder) -> builder.equal(root.get(CLASSIFICATION_STATE).get("ecosystemCode"), tagFilter.ecosystemCode()));
        }
        if (tagFilter.isNotable() != null) {
            specifications.add((root, query, builder) -> builder.equal(root.get(CLASSIFICATION_STATE).get("isNotable"), tagFilter.isNotable()));
        }
        if (tagFilter.hasInterests()) {
            specifications.add(matchesAnyInterest(tagFilter));
        }
        if (tagFilter.collectedSince() != null) {
            specifications.add((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("collectedAt"), tagFilter.collectedSince()));
        }
        return specifications;
    }

    // 내 기술: 제품 code ∈ 목록 or 분야 code ∈ 목록 or 생태계 code ∈ 목록 (빈 목록은 건너뜀)
    private static Specification<Article> matchesAnyInterest(ArticleTagFilter tagFilter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!tagFilter.interestProductCodes().isEmpty()) {
                predicates.add(root.get(CLASSIFICATION_STATE).get("productCode").in(tagFilter.interestProductCodes()));
            }
            if (!tagFilter.interestFieldCodes().isEmpty()) {
                predicates.add(root.get(CLASSIFICATION_STATE).get("fieldCode").in(tagFilter.interestFieldCodes()));
            }
            if (!tagFilter.interestEcosystemCodes().isEmpty()) {
                predicates.add(root.get(CLASSIFICATION_STATE).get("ecosystemCode").in(tagFilter.interestEcosystemCodes()));
            }
            return builder.or(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Article> hasKind(ArticleKind kind) {
        return (root, query, builder) -> builder.equal(root.get("kind"), kind);
    }

    public static Specification<Article> hasRegion(ArticleRegion region) {
        return (root, query, builder) -> builder.equal(root.get("region"), region);
    }

    public static Specification<Article> hasTopic(ArticleTopic topic) {
        return (root, query, builder) -> builder.equal(root.get("aiAnalysisState").get("topic"), topic);
    }

    public static Specification<Article> hasImportanceAtLeast(ArticleImportance minimumImportance) {
        return (root, query, builder) -> root.get("importance").in(minimumImportance.toLevelsAtLeast());
    }

    public static Specification<Article> hasSourceCode(String sourceCode) {
        return (root, query, builder) -> builder.equal(root.get("source").get("code"), sourceCode);
    }

    /**
     * 키워드 일치: trim 후 정확히 일치 (대소문자 무시)
     */
    public static Specification<Article> hasKeyword(String keyword) {
        String lowerCaseKeyword = keyword.strip().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> hasAnyKeyword(root, query, builder,
                keywordExpression -> builder.equal(builder.lower(keywordExpression), lowerCaseKeyword));
    }

    /**
     * 검색어 포함 (대소문자 무시 부분 일치): 대상 5개 — 제목, 번역 제목, 회사·제품 이름, CVE 번호, 키워드
     */
    public static Specification<Article> containsSearchText(String searchText) {
        String containsPattern = LIKE_WILDCARD + toEscapedLikeText(searchText.toLowerCase(Locale.ROOT)) + LIKE_WILDCARD;
        return (root, query, builder) -> {
            Function<Expression<String>, Predicate> containsSearchText = textExpression ->
                    builder.like(builder.lower(textExpression), containsPattern, LIKE_ESCAPE_CHARACTER);
            return builder.or(
                    containsSearchText.apply(root.get("title")),
                    containsSearchText.apply(root.get("aiAnalysisState").get("translatedTitle")),
                    containsSearchText.apply(root.get("aiAnalysisState").get("subjectName")),
                    containsSearchText.apply(root.get("cveId")),
                    hasAnyKeyword(root, query, builder, containsSearchText));
        };
    }

    public static Specification<Article> isPublishedSince(Instant start) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("publishedAt"), start);
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

    public static Specification<Article> isUrgent() {
        return (root, query, builder) -> builder.isTrue(root.get("isUrgent"));
    }

    public static Specification<Article> isUrgentPublishedSince(Instant start) {
        return (root, query, builder) -> builder.and(
                builder.isTrue(root.get("isUrgent")),
                builder.greaterThanOrEqualTo(root.get("publishedAt"), start));
    }

    /**
     * 홈 최신 목록에 올릴 소식: 뉴스, 국내 공지(KISA), 중요도 NOTABLE 이상
     * (건수가 많은 일반 CVE·해외 배포판 권고가 홈을 덮지 않게, 그것들은 취약점 탭에서 봄)
     */
    public static Specification<Article> isBriefingStreamItem() {
        return (root, query, builder) -> builder.or(
                builder.equal(root.get("kind"), ArticleKind.NEWS),
                builder.and(
                        builder.equal(root.get("kind"), ArticleKind.ADVISORY),
                        builder.equal(root.get("region"), ArticleRegion.DOMESTIC)),
                root.get("importance").in(ArticleImportance.NOTABLE.toLevelsAtLeast()));
    }

    public static Specification<Article> hasIdOtherThan(Long id) {
        return (root, query, builder) -> builder.notEqual(root.get("id"), id);
    }

    // 조건에 맞는 키워드가 하나라도 있는지 (exists 하위 조회: 키워드 여러 개가 맞아도 기사가 한 번만 나옴)
    private static Predicate hasAnyKeyword(Root<Article> root, CriteriaQuery<?> query, CriteriaBuilder builder,
                                           Function<Expression<String>, Predicate> keywordCondition) {
        Subquery<Integer> keywordSubquery = query.subquery(Integer.class);
        Join<Article, String> keyword = keywordSubquery.correlate(root).join("aiAnalysisState").join("keywords");
        return builder.exists(keywordSubquery.select(builder.literal(1)).where(keywordCondition.apply(keyword)));
    }

    // LIKE 특수문자 3개(\, %, _) 앞에 이스케이프 문자를 붙임
    private static String toEscapedLikeText(String text) {
        StringBuilder escapedText = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (character == LIKE_ESCAPE_CHARACTER || character == '%' || character == '_') {
                escapedText.append(LIKE_ESCAPE_CHARACTER);
            }
            escapedText.append(character);
        }
        return escapedText.toString();
    }
}
