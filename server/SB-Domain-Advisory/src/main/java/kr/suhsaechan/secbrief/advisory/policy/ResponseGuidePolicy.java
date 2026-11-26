package kr.suhsaechan.secbrief.advisory.policy;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideMatchReason;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideSection;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;

/**
 * 대응 모아보기 기준 값: 구역 판정(출처 종류), 묶는 기간·건수, 제품 일치를 쓰지 않는 경우 (조회 SQL 은 ArticleRepository.findResponseGuideCandidates)
 * 묶는 규칙 3개: 같은 CVE(1년) > 같은 제품 이름(±14일) > KISA 제조사 공지의 제조사 = 상대 제품 이름 앞 낱말(±14일)
 */
public final class ResponseGuidePolicy {

    // 구역마다 보여 줄 최대 건수
    public static final int MAXIMUM_ITEM_COUNT_PER_SECTION = 5;
    // 조회 SQL 이 돌려주는 후보 상한 (구역 나누기·걸러내기 전, 구역별 전체 건수도 이 안에서 셈)
    public static final int MAXIMUM_CANDIDATE_COUNT = 100;
    // CVE 일치는 정확해서 길게 봄 (기사 게시일 기준 1년 전부터)
    public static final Duration CVE_MATCH_LOOKBACK = Duration.ofDays(365);
    // 제품 이름 일치는 같은 이름의 다른 사건이 섞이기 쉬워 게시일 앞뒤 14일만
    public static final Duration PRODUCT_MATCH_WINDOW = Duration.ofDays(14);
    // 제품 이름 최소 글자 수 (F5 같은 두 글자 제조사까지)
    private static final int MINIMUM_PRODUCT_NAME_LENGTH = 2;
    // 제품 이름으로 묶지 않는 주제 3개: 행사·정책·개인정보 소식은 같은 회사 이름이어도 대응 자료가 아님
    public static final Set<ArticleTopic> PRODUCT_MATCH_EXCLUDED_TOPICS =
            Set.of(ArticleTopic.EVENT, ArticleTopic.POLICY, ArticleTopic.PRIVACY);
    // 제조사 단위 공지 출처: KISA 보안공지 「X 제품 보안 업데이트 권고」 (회사·제품 이름 = 제조사)
    public static final String VENDOR_NOTICE_SOURCE_CODE = SourceCode.KISA;

    private ResponseGuidePolicy() {
    }

    /**
     * 출처 > 구역: ADVISORY 공식 대응, VULNERABILITY 확인된 정보, NEWS 관련 기사
     * KISA 경보단계는 사이버 위기 경보 수준이라 특정 취약점의 대응 자료가 아님 (빈 값)
     */
    public static Optional<ResponseGuideSection> toSection(String sourceCode, ArticleKind sourceKind) {
        if (SourceCode.KISA_ALERT_LEVEL.equals(sourceCode)) {
            return Optional.empty();
        }
        return Optional.of(switch (sourceKind) {
            case ADVISORY -> ResponseGuideSection.OFFICIAL;
            case VULNERABILITY -> ResponseGuideSection.FACT;
            case NEWS -> ResponseGuideSection.NEWS;
        });
    }

    /**
     * 기사 > 제품 일치에 쓸 회사·제품 이름 (쓰지 않으면 빈 문자열)
     * 빈 값 조건 3개: 이름 없음, 너무 짧음, 행사·정책·개인정보 주제
     */
    public static String toProductMatchName(Article article) {
        String subjectName = article.getSubjectName();
        if (subjectName == null || subjectName.isBlank() || subjectName.strip().length() < MINIMUM_PRODUCT_NAME_LENGTH) {
            return "";
        }
        return PRODUCT_MATCH_EXCLUDED_TOPICS.contains(article.getTopic()) ? "" : subjectName.strip();
    }

    /**
     * 후보를 싣는지 (같은 CVE 는 항상 실음), 제품·제조사 일치를 버리는 경우 2개:
     * 양쪽 모두 CVE 가 있는데 겹치지 않음 (다른 취약점), CVE 가 있는 기사에 다른 CVE 항목(확인된 정보)을 제품 이름만으로 붙임
     */
    public static boolean isAcceptedCandidate(boolean hasViewedCveIds, boolean hasCandidateCveIds,
                                              ResponseGuideSection section, ResponseGuideMatchReason matchReason) {
        if (matchReason == ResponseGuideMatchReason.SAME_CVE || !hasViewedCveIds) {
            return true;
        }
        return !hasCandidateCveIds && section != ResponseGuideSection.FACT;
    }

    /** 제조사 단위 공지 출처인지 (KISA 보안공지) */
    public static boolean isVendorNoticeSource(String sourceCode) {
        return VENDOR_NOTICE_SOURCE_CODE.equals(sourceCode);
    }
}
