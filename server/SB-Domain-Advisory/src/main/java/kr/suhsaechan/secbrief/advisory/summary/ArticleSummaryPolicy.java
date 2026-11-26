package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy;

/**
 * AI 요약 검증 (순수 함수): 입력(제목 + 요약 입력)에 근거한 글만 받음
 * 거르는 조건 5개: 빈 값, 길이 상한 초과, 한글 없음, 입력에 없는 숫자 표기, 입력 문장 그대로 옮김
 */
public final class ArticleSummaryPolicy {

    // article.summary 열 길이
    public static final int MAXIMUM_SUMMARY_LENGTH = 1500;
    // article.summary_action_guide 열 길이
    public static final int MAXIMUM_ACTION_GUIDE_LENGTH = 800;
    // 핵심 정리 1개 최대 길이 (짧은 문장)
    public static final int MAXIMUM_KEY_POINT_LENGTH = 200;
    // 핵심 정리 최대 개수
    public static final int MAXIMUM_KEY_POINT_COUNT = 4;
    // 입력과 글자가 똑같은 구간이 이 길이 이상이면 옮겨 실은 것으로 봄 (한국어 한 문장 안팎, 제품 이름·CVE ID 는 이보다 짧음)
    public static final int MINIMUM_VERBATIM_COPY_LENGTH = 60;

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    private ArticleSummaryPolicy() {
    }

    /**
     * 요약 검증 > 받아들인 요약(다듬은 값) 또는 거부 사유 (요약이 걸리면 그 건 거부, 핵심 정리·대응 안내는 걸린 것만 버림)
     */
    public static ArticleSummaryValidation validate(String title, String sourceText, ArticleSummary summary) {
        String summaryText = toStripped(summary.summary());
        String summaryRejectionReason = findRejectionReason(title, sourceText, summaryText, MAXIMUM_SUMMARY_LENGTH);
        if (summaryRejectionReason != null) {
            return ArticleSummaryValidation.rejected("summary " + summaryRejectionReason);
        }
        List<String> acceptedKeyPoints = summary.keyPoints().stream()
                .map(ArticleSummaryPolicy::toStripped)
                .filter(keyPoint -> findRejectionReason(title, sourceText, keyPoint, MAXIMUM_KEY_POINT_LENGTH) == null)
                .limit(MAXIMUM_KEY_POINT_COUNT)
                .toList();
        String actionGuide = toStripped(summary.actionGuide());
        String acceptedActionGuide =
                findRejectionReason(title, sourceText, actionGuide, MAXIMUM_ACTION_GUIDE_LENGTH) == null ? actionGuide : null;
        return ArticleSummaryValidation.accepted(new ArticleSummary(summaryText, acceptedKeyPoints, acceptedActionGuide));
    }

    // 거부 사유 (통과면 null): 숫자 표기는 제목 + 요약 입력에서, 옮겨 싣기는 요약 입력에서만 확인 (제목 인용은 허용)
    private static String findRejectionReason(String title, String sourceText, String generatedText, int maximumLength) {
        if (generatedText.isEmpty()) {
            return "is empty";
        }
        if (generatedText.length() > maximumLength) {
            return "is too long";
        }
        if (!HANGUL.matcher(generatedText).find()) {
            return "has no korean";
        }
        if (SourceGroundingPolicy.hasNumberNotInSource(title + " " + sourceText, generatedText)) {
            return "has number not in source";
        }
        if (SourceGroundingPolicy.hasVerbatimCopy(sourceText, generatedText, MINIMUM_VERBATIM_COPY_LENGTH)) {
            return "copies source text";
        }
        return null;
    }

    private static String toStripped(String text) {
        return text == null ? "" : text.strip();
    }
}
