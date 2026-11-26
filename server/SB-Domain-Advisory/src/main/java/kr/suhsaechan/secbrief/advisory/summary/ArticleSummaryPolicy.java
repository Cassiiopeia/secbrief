package kr.suhsaechan.secbrief.advisory.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy;

/**
 * AI 요약 검증 (순수 함수): 입력(제목 + 요약 입력)에 근거한 글만 받음
 * 거르는 조건 6개: 빈 값, 길이 상한 초과, 한글 없음, 글 자체를 가리키는 말, 입력에 근거 없는 숫자 표기, 입력 문장 그대로 옮김
 * 숫자: 같은 표기가 입력에 있어야 함 (영어 달 이름·수 낱말을 옮긴 숫자만 예외), 옮겨 싣기: 제품 이름·버전 나열·식별자는 제외
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
    // 입력과 글자가 똑같은 문장 구간이 이 길이 이상이면 옮겨 실은 것으로 봄 (한국어 한 문장 안팎)
    public static final int MINIMUM_VERBATIM_COPY_LENGTH = 60;

    // 거부 사유 6개 (로그·고쳐 쓰기 요청 공통 문구: summary-harness/repair-rules.md 가 사유마다 고치는 법을 적음)
    public static final String REASON_EMPTY = "is empty";
    public static final String REASON_TOO_LONG = "is too long";
    public static final String REASON_NO_KOREAN = "has no korean";
    public static final String REASON_MENTIONS_SOURCE = "talks about the source text";
    public static final String REASON_NUMBER_NOT_IN_SOURCE = "has number not in source";
    public static final String REASON_COPIES_SOURCE = "copies source text";

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    // 글 자체를 가리키는 말: 요약은 사실만 말함 ("원문에 따르면", "입력에 명시되지 않았어요" 는 읽는 사람에게 뜻이 없음)
    // "입력" 단독은 취약점 설명에 흔해 제외 (사용자 입력을 검증하지 않아요)
    private static final Pattern SOURCE_MENTION =
            Pattern.compile("원문|입력에 따르면|입력에 (명시|언급|적혀|나와)|이 기사|기사에 따르면|기사가 전했");
    private static final String NUMBER_FRAGMENT_SEPARATOR = ", ";

    private ArticleSummaryPolicy() {
    }

    /**
     * 요약 검증 > 받아들인 요약(다듬은 값) 또는 거부 사유 + 걸린 곳 목록
     * 요약이 걸리면 그 건 거부, 핵심 정리·대응 안내는 걸린 것만 버림 (빈 핵심 정리·빈 대응 안내는 걸린 곳으로 세지 않음)
     */
    public static ArticleSummaryValidation validate(String title, String sourceText, ArticleSummary summary) {
        List<SummaryProblem> problems = new ArrayList<>();

        String summaryText = toStripped(summary.summary());
        Optional<SummaryProblem> summaryProblem = findProblem(
                ArticleSummaryPrompt.SUMMARY_FIELD, title, sourceText, summaryText, MAXIMUM_SUMMARY_LENGTH);
        summaryProblem.ifPresent(problems::add);

        List<String> acceptedKeyPoints = new ArrayList<>();
        for (String rawKeyPoint : summary.keyPoints()) {
            String keyPoint = toStripped(rawKeyPoint);
            if (keyPoint.isEmpty()) {
                continue;
            }
            Optional<SummaryProblem> keyPointProblem = findProblem(
                    ArticleSummaryPrompt.KEY_POINTS_FIELD, title, sourceText, keyPoint, MAXIMUM_KEY_POINT_LENGTH);
            keyPointProblem.ifPresent(problems::add);
            if (keyPointProblem.isEmpty() && acceptedKeyPoints.size() < MAXIMUM_KEY_POINT_COUNT) {
                acceptedKeyPoints.add(keyPoint);
            }
        }

        String actionGuide = toStripped(summary.actionGuide());
        Optional<SummaryProblem> actionGuideProblem = actionGuide.isEmpty() ? Optional.empty() : findProblem(
                ArticleSummaryPrompt.ACTION_GUIDE_FIELD, title, sourceText, actionGuide, MAXIMUM_ACTION_GUIDE_LENGTH);
        actionGuideProblem.ifPresent(problems::add);

        if (summaryProblem.isPresent()) {
            return ArticleSummaryValidation.rejected(
                    ArticleSummaryPrompt.SUMMARY_FIELD + " " + summaryProblem.get().reason(), problems);
        }
        String acceptedActionGuide = actionGuide.isEmpty() || actionGuideProblem.isPresent() ? null : actionGuide;
        return ArticleSummaryValidation.accepted(
                new ArticleSummary(summaryText, acceptedKeyPoints, acceptedActionGuide), problems);
    }

    // 걸린 곳 (통과면 빈 값): 숫자 표기는 제목 + 요약 입력에서, 옮겨 싣기는 요약 입력에서만 확인 (제목 인용은 허용)
    private static Optional<SummaryProblem> findProblem(String fieldName, String title, String sourceText,
                                                        String generatedText, int maximumLength) {
        if (generatedText.isEmpty()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_EMPTY, null));
        }
        if (generatedText.length() > maximumLength) {
            return Optional.of(new SummaryProblem(fieldName, REASON_TOO_LONG, null));
        }
        if (!HANGUL.matcher(generatedText).find()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_NO_KOREAN, null));
        }
        Matcher sourceMention = SOURCE_MENTION.matcher(generatedText);
        if (sourceMention.find()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_MENTIONS_SOURCE, sourceMention.group()));
        }
        List<String> ungroundedNumbers = SourceGroundingPolicy.findUngroundedNumbers(title + " " + sourceText, generatedText);
        if (!ungroundedNumbers.isEmpty()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_NUMBER_NOT_IN_SOURCE,
                    String.join(NUMBER_FRAGMENT_SEPARATOR, ungroundedNumbers)));
        }
        return SourceGroundingPolicy.findCopiedProse(sourceText, generatedText, MINIMUM_VERBATIM_COPY_LENGTH)
                .map(copiedFragment -> new SummaryProblem(fieldName, REASON_COPIES_SOURCE, copiedFragment));
    }

    private static String toStripped(String text) {
        return text == null ? "" : text.strip();
    }
}
