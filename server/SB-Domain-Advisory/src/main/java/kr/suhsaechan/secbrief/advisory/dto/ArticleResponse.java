package kr.suhsaechan.secbrief.advisory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;

/**
 * 기사 응답: 원문 제목 + AI 한국어 제목(없으면 null), 원문 링크·게시자·게시일, CVE 정보, 대표 이미지 경로,
 * 중요도·제목 속 회사·제품 이름·키워드(AI 판단), 출처 코드, 브리핑의 한 줄 결론, 분류 태그 code·주요 (AI·이미지 상태는 내보내지 않음)
 */
public record ArticleResponse(
        Long id,
        ArticleKind kind,
        ArticleRegion region,
        ArticleTopic topic,
        String title,
        String translatedTitle,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        // boolean 은 is 접두사 그대로 JSON 이름으로 (앱 모델과 같은 이름)
        @JsonProperty("isPublishedAtEstimated") boolean isPublishedAtEstimated,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        @JsonProperty("isKev") boolean isKev,
        @JsonProperty("isUrgent") boolean isUrgent,
        // 저장된 대표 이미지 경로 (서버 주소 뒤에 붙여 씀, 없으면 null)
        String imagePath,
        ArticleImportance importance,
        // 제목에 나온 회사·제품 이름 (없으면 null)
        String subjectName,
        // 출처 코드 (SourceCode: KISA, KISA_REPORT, NVD ...)
        String sourceCode,
        // AI 가 제목에서 뽑은 키워드 (# 없음, 대소문자 무시 글자순, 없으면 빈 목록)
        List<String> keywords,
        // 브리핑의 한 줄 결론 (브리핑이 없으면 null)
        String oneLine,
        // 분류 태그 code (catalog_tag, 분류 전·없으면 null, 이름은 GET /api/v1/catalog/tags)
        String fieldCode,
        String ecosystemCode,
        // GitHub 권고 첫 패키지 이름 (원문 그대로)
        String packageName,
        String productCode,
        // 주요 (KEV, 널리 쓰는 제품, CVSS 9.0 이상이면서 AI 가 일반이 아니라고 봄, 헤드라인)
        @JsonProperty("isNotable") boolean isNotable
) {

    // ArticleController 의 이미지 경로와 같아야 함: /api/v1/articles/{id}/image
    private static final String IMAGE_PATH_FORMAT = "/api/v1/articles/%d/image";

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getKind(),
                article.getRegion(),
                article.getTopic(),
                article.getTitle(),
                article.getTranslatedTitle(),
                article.getOriginalUrl(),
                article.getPublisherName(),
                article.getPublishedAt(),
                article.isPublishedAtEstimated(),
                article.getCveId(),
                article.getCvssScore(),
                article.getCvssVersion(),
                article.isKev(),
                article.isUrgent(),
                article.getImageState().getStatus() == ImageStatus.STORED ? toImagePath(article.getId()) : null,
                article.getImportance(),
                article.getSubjectName(),
                article.getSource().getCode(),
                article.getKeywords(),
                article.getSummaryState().hasBrief() ? article.getSummaryState().getOneLine() : null,
                article.getClassificationState().getFieldCode(),
                article.getClassificationState().getEcosystemCode(),
                article.getClassificationState().getPackageName(),
                article.getClassificationState().getProductCode(),
                article.getClassificationState().isNotable());
    }

    public static String toImagePath(Long articleId) {
        return IMAGE_PATH_FORMAT.formatted(articleId);
    }
}
