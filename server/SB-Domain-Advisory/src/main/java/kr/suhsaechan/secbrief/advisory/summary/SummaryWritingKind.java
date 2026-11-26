package kr.suhsaechan.secbrief.advisory.summary;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 요약 작성 규칙의 글 종류 6개: 출처 코드·기사 종류·주제 > 글 종류
 * 종류마다 작성 안내 파일 1개 (resources/summary-harness/kind-{guideName}.md)
 */
@Getter
@RequiredArgsConstructor
public enum SummaryWritingKind {
    // NVD·CISA KEV: 공개 취약점 설명 한 단락 (KEV 는 조치 문구가 붙음)
    CVE_ENTRY("cve-entry"),
    // GitHub 보안 권고: 패키지·영향 버전·조치 버전·재현 코드가 섞인 긴 설명
    PACKAGE_ADVISORY("package-advisory"),
    // 배포판·런타임 공식 권고 (Ubuntu, Debian, Red Hat, Node.js): 정형 문구가 많은 짧은 공지
    VENDOR_ADVISORY("vendor-advisory"),
    // KISA 보호나라 4곳: 보안공지, 취약점 정보, 보고서·가이드, 경보단계 (한국어 입력)
    KISA_NOTICE("kisa-notice"),
    // 언론 기사: 피드가 준 요약문 (끝이 잘린 글 포함)
    NEWS_ARTICLE("news-article"),
    // 행사 소식 (컨퍼런스·웨비나·교육·발표 모집): 출처와 무관하게 주제가 EVENT 인 글
    EVENT_NOTICE("event-notice");

    private final String guideName;

    /**
     * 글 종류 판정 순서: 행사 주제 > KISA 출처 > GitHub 권고 출처 > 기사 종류 (취약점, 공지, 그 밖은 기사)
     */
    public static SummaryWritingKind of(String sourceCode, ArticleKind articleKind, ArticleTopic topic) {
        if (topic == ArticleTopic.EVENT) {
            return EVENT_NOTICE;
        }
        if (SourceCode.isKisa(sourceCode)) {
            return KISA_NOTICE;
        }
        if (SourceCode.GHSA.equals(sourceCode) || SourceCode.GHSA_HIGH.equals(sourceCode)) {
            return PACKAGE_ADVISORY;
        }
        if (articleKind == ArticleKind.VULNERABILITY) {
            return CVE_ENTRY;
        }
        return articleKind == ArticleKind.ADVISORY ? VENDOR_ADVISORY : NEWS_ARTICLE;
    }
}
