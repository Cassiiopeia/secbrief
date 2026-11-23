package kr.suhsaechan.secbrief.advisory.collector;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 수집기가 출처에서 읽은 원본 항목 (정규화 전): 제목, 링크, 게시자, 게시일, CVE 정보
 * publishedAt 은 출처가 안 주면 null, 본문은 담지 않음
 */
public record CollectedArticle(
        String title,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        boolean isKev
) {
}
