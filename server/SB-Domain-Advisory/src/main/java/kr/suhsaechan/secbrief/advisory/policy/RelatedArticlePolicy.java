package kr.suhsaechan.secbrief.advisory.policy;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 관련 기사 기준 값: 최대 건수, 조회 기간, 기사가 다루는 CVE ID 찾기 (조회 SQL 은 ArticleRepository.findRelatedArticleIds)
 */
public final class RelatedArticlePolicy {

    // 상세 화면 아래에 보여 줄 관련 기사 수
    public static final int MAXIMUM_RELATED_ARTICLE_COUNT = 6;
    // 이 기간 안에 게시된 기사만 관련 기사로 (오래된 소식은 지금 볼 이슈가 아님)
    public static final Duration RELATED_ARTICLE_LOOKBACK = Duration.ofDays(30);
    // 제목에서 읽는 CVE ID 수 상한 (여러 CVE 를 묶은 공지)
    private static final int MAXIMUM_CVE_ID_COUNT = 5;
    private static final Pattern CVE_ID = Pattern.compile("CVE-\\d{4}-\\d{4,7}", Pattern.CASE_INSENSITIVE);

    private RelatedArticlePolicy() {
    }

    /**
     * 기사의 CVE ID(전용 열 + 제목에 적힌 것) > 다른 기사의 cve_id·제목과 맞출 정규식, CVE 가 없으면 빈 문자열
     * 예: (CVE-2026-1111|CVE-2026-2222)(?!\d) — 뒤에 숫자가 이어지는 다른 번호는 제외
     */
    public static String toCveIdPattern(String cveId, String title) {
        Set<String> cveIds = new LinkedHashSet<>();
        Matcher matcher = CVE_ID.matcher((cveId == null ? "" : cveId) + " " + title);
        while (matcher.find() && cveIds.size() < MAXIMUM_CVE_ID_COUNT) {
            cveIds.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return cveIds.isEmpty() ? "" : "(" + String.join("|", cveIds) + ")(?!\\d)";
    }
}
