package kr.suhsaechan.secbrief.advisory.collector;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;

/**
 * 수집기가 출처에서 읽은 원본 항목 (정규화 전): 제목, 링크, 게시자, 게시일, CVE 정보, 대표 이미지 주소, 요약 입력, 사실 값
 * publishedAt·imageSourceUrl·sourceText 는 출처가 안 주면 null, 기사 본문 페이지는 읽지 않음 (피드·공개 API 가 준 글만)
 */
public record CollectedArticle(
        String title,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        boolean isKev,
        String imageSourceUrl,
        // AI 요약 입력: 피드 요약문·공개 취약점 설명 (정리 전, 없으면 null)
        String sourceText,
        // 원문 데이터의 사실 값 (없으면 빈 목록)
        List<SourceFact> sourceFacts
) {

    public CollectedArticle {
        sourceFacts = sourceFacts == null ? List.of() : List.copyOf(sourceFacts);
    }

    /**
     * 요약 입력·사실 값 없는 항목 (이미지 주소만)
     */
    public CollectedArticle(String title, String originalUrl, String publisherName, Instant publishedAt,
                            String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev, String imageSourceUrl) {
        this(title, originalUrl, publisherName, publishedAt, cveId, cvssScore, cvssVersion, isKev, imageSourceUrl,
                null, List.of());
    }

    /**
     * 이미지·요약 입력·사실 값 없는 항목
     */
    public CollectedArticle(String title, String originalUrl, String publisherName, Instant publishedAt,
                            String cveId, BigDecimal cvssScore, String cvssVersion, boolean isKev) {
        this(title, originalUrl, publisherName, publishedAt, cveId, cvssScore, cvssVersion, isKev, null);
    }

    /**
     * 이미지를 주지 않는 출처용 (KEV, NVD, GHSA): 요약 입력 + 사실 값
     */
    public static CollectedArticle ofVulnerability(String title, String originalUrl, String publisherName,
                                                   Instant publishedAt, String cveId, BigDecimal cvssScore,
                                                   String cvssVersion, boolean isKev, String sourceText,
                                                   List<SourceFact> sourceFacts) {
        return new CollectedArticle(title, originalUrl, publisherName, publishedAt, cveId, cvssScore, cvssVersion,
                isKev, null, sourceText, sourceFacts);
    }
}
