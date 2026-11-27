package kr.suhsaechan.secbrief.advisory.policy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;

/**
 * 정규화를 마친 기사 초안: 저장 직전 값 (주제는 없음, 저장 시 GENERAL·PENDING)
 */
public record ArticleDraft(
        ArticleKind kind,
        ArticleRegion region,
        String title,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        boolean isPublishedAtEstimated,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        boolean isKev,
        boolean isUrgent,
        // 피드가 준 대표 이미지 주소 (없으면 null)
        String imageSourceUrl,
        // AI 요약 입력: HTML 을 벗긴 요약문·공개 설명 (없으면 null)
        String sourceText,
        // 원문 데이터의 사실 값 (없으면 빈 목록)
        List<SourceFact> sourceFacts
) {

    public ArticleDraft {
        sourceFacts = sourceFacts == null ? List.of() : List.copyOf(sourceFacts);
    }

    /**
     * 요약 입력만 바꾼 초안 (KISA 게시글 본문을 수집 때 받은 경우, #57)
     */
    public ArticleDraft withSourceText(String newSourceText) {
        return new ArticleDraft(kind, region, title, originalUrl, publisherName, publishedAt, isPublishedAtEstimated, cveId,
                cvssScore, cvssVersion, isKev, isUrgent, imageSourceUrl, newSourceText, sourceFacts);
    }

    /**
     * 요약 입력·사실 값 없는 초안 (이미지 주소만)
     */
    public ArticleDraft(ArticleKind kind, ArticleRegion region, String title, String originalUrl, String publisherName,
                        Instant publishedAt, boolean isPublishedAtEstimated, String cveId, BigDecimal cvssScore,
                        String cvssVersion, boolean isKev, boolean isUrgent, String imageSourceUrl) {
        this(kind, region, title, originalUrl, publisherName, publishedAt, isPublishedAtEstimated, cveId, cvssScore,
                cvssVersion, isKev, isUrgent, imageSourceUrl, null, List.of());
    }

    /**
     * 대표 이미지·요약 입력·사실 값 없는 초안
     */
    public ArticleDraft(ArticleKind kind, ArticleRegion region, String title, String originalUrl, String publisherName,
                        Instant publishedAt, boolean isPublishedAtEstimated, String cveId, BigDecimal cvssScore,
                        String cvssVersion, boolean isKev, boolean isUrgent) {
        this(kind, region, title, originalUrl, publisherName, publishedAt, isPublishedAtEstimated, cveId, cvssScore,
                cvssVersion, isKev, isUrgent, null);
    }
}
