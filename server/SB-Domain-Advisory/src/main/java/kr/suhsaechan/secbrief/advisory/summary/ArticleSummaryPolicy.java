package kr.suhsaechan.secbrief.advisory.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy;
import kr.suhsaechan.secbrief.advisory.policy.TextSimilarityPolicy;

/**
 * AI 브리핑 검증 (순수 함수): 입력(제목 + 요약 입력)에 근거한 글만 받음, 필드마다 따로 확인
 * 필수 2개(무슨 일, 한 줄 결론)가 걸리면 그 건 거부, 선택 필드·목록 항목은 걸린 것만 버림 (영향 대상의 조건만 걸리면 조건만 버림)
 * 거르는 조건 12개: 빈 값, 길이 상한 초과, 한글 없음, 글 자체를 가리키는 말, 입력에 근거 없는 숫자 표기, 입력 문장 그대로 옮김,
 * 입력에 없는 대상 이름, 대상 자리에 식별자, 문장으로 쓴 조건, 해요체로 끝나지 않는 한 줄 결론·대응,
 * 한 줄 결론을 되풀이한 무슨 일, 무슨 일을 되풀이한 왜 중요
 */
public final class ArticleSummaryPolicy {

    // 한 줄 결론 최대 길이 (작성 규칙은 40자 안팎: 앱 목록 카드에서 12pt 두 줄, 40자쯤부터 잘림)
    public static final int MAXIMUM_ONE_LINE_LENGTH = 60;
    // 무슨 일 최대 길이 (작성 규칙은 1~2문장 160자 안팎)
    public static final int MAXIMUM_WHAT_HAPPENED_LENGTH = 300;
    // 왜 중요 최대 길이 (작성 규칙은 1~2문장 140자 안팎)
    public static final int MAXIMUM_WHY_IT_MATTERS_LENGTH = 240;
    // 영향 대상 이름 최대 길이 (긴 제품 이름: Red Hat Enterprise Linux 9.4 Update Services for SAP Solutions 62자)
    public static final int MAXIMUM_AFFECTED_TARGET_LENGTH = 120;
    // 영향 대상 조건 최대 길이 (앱이 고정폭 한 덩어리로 보여 주는 짧은 값: 작성 규칙은 40자 안팎)
    public static final int MAXIMUM_AFFECTED_DETAIL_LENGTH = 80;
    // 대응 1개 최대 길이 (작성 규칙은 60자 안팎)
    public static final int MAXIMUM_ACTION_LENGTH = 120;
    public static final int MAXIMUM_AFFECTED_COUNT = 4;
    public static final int MAXIMUM_ACTION_COUNT = 4;
    // 입력과 글자가 똑같은 문장 구간이 이 길이 이상이면 옮겨 실은 것으로 봄 (한국어 한 문장 안팎)
    public static final int MINIMUM_VERBATIM_COPY_LENGTH = 60;
    // 두 문장의 한글 묶음 겹침이 이 값 이상이면 되풀이로 봄 (평가 표본: 되풀이로 읽히는 쌍 0.50 이상, 다른 내용 0.48 이하)
    public static final double MINIMUM_REPEAT_SIMILARITY = 0.5;

    // 검증에 걸린 곳의 필드 이름 (목록 항목: 영향 대상의 이름·조건)
    public static final String AFFECTED_TARGET_PROBLEM_FIELD =
            ArticleSummaryPrompt.AFFECTED_FIELD + "." + ArticleSummaryPrompt.AFFECTED_TARGET_FIELD;
    public static final String AFFECTED_DETAIL_PROBLEM_FIELD =
            ArticleSummaryPrompt.AFFECTED_FIELD + "." + ArticleSummaryPrompt.AFFECTED_DETAIL_FIELD;

    // 거부 사유 12개 (로그·고쳐 쓰기 요청 공통 문구: summary-harness/repair-rules.md 가 사유마다 고치는 법을 적음)
    public static final String REASON_EMPTY = "is empty";
    public static final String REASON_TOO_LONG = "is too long";
    public static final String REASON_NO_KOREAN = "has no korean";
    public static final String REASON_MENTIONS_SOURCE = "talks about the source text";
    public static final String REASON_NUMBER_NOT_IN_SOURCE = "has number not in source";
    public static final String REASON_COPIES_SOURCE = "copies source text";
    public static final String REASON_NOT_IN_SOURCE = "is not written in the input";
    public static final String REASON_IDENTIFIER = "is an identifier";
    public static final String REASON_SENTENCE = "is a sentence";
    public static final String REASON_NOT_POLITE_SENTENCE = "is not a 해요체 sentence";
    public static final String REASON_REPEATS_ONE_LINE = "repeats oneLine";
    public static final String REASON_REPEATS_WHAT_HAPPENED = "repeats whatHappened";

    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");
    // 글 자체를 가리키는 말: 브리핑은 사실만 말함 ("원문에 따르면", "입력에 명시되지 않았어요" 는 읽는 사람에게 뜻이 없음)
    // "입력" 단독은 취약점 설명에 흔해 제외 (사용자 입력을 검증하지 않아요)
    private static final Pattern SOURCE_MENTION =
            Pattern.compile("원문|입력에 따르면|입력에 (명시|언급|적혀|나와)|이 기사|기사(는|가|에)|제목(은|에서)|소개글|(설명|언급|소개)돼요");
    // 대상 자리에 온 CVE ID: 앱이 따로 보여 주는 식별자라 영향 대상이 아님
    private static final Pattern CVE_ID = Pattern.compile("(?i)CVE-\\d{4}-\\d{4,}");
    // 문장으로 쓴 조건 (해요체·합쇼체 끝): 조건은 짧은 값이고 영향 설명은 '왜 중요' 자리
    private static final Pattern SENTENCE_ENDING = Pattern.compile("(요|니다)[.!]?$");
    // 해요체 문장 끝: 한 줄 결론(목록 카드)과 대응(번호 목록)은 "~요." 로 끝나야 함 (신문 제목처럼 명사로 끝난 글 제외)
    private static final Pattern POLITE_SENTENCE_ENDING = Pattern.compile("요[.!?]?$");
    // 한국어 문장 끝 (한글 뒤의 마침표·물음표·느낌표 + 공백): 버전 숫자 사이의 점은 문장 끝이 아님
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[가-힣])[.!?]\\s+");
    private static final String NUMBER_FRAGMENT_SEPARATOR = ", ";

    private ArticleSummaryPolicy() {
    }

    /**
     * 브리핑 검증 > 받아들인 브리핑(다듬은 값) 또는 거부 사유 + 걸린 곳 목록
     * 빈 선택 필드·빈 목록은 걸린 곳으로 세지 않음 (입력에 근거가 없으면 비우는 것이 규칙)
     */
    public static ArticleSummaryValidation validate(String title, String sourceText, ArticleBrief brief) {
        SourceInput input = new SourceInput(title, sourceText);
        List<SummaryProblem> problems = new ArrayList<>();

        // 필수 2개: 걸리면 그 건 거부
        String whatHappened = toSingleLine(brief.whatHappened());
        String oneLine = toSingleLine(brief.oneLine());
        Optional<SummaryProblem> whatHappenedProblem = findSentenceProblem(
                ArticleSummaryPrompt.WHAT_HAPPENED_FIELD, input, whatHappened, MAXIMUM_WHAT_HAPPENED_LENGTH)
                .or(() -> findRepeatProblem(whatHappened, oneLine));
        whatHappenedProblem.ifPresent(problems::add);
        Optional<SummaryProblem> oneLineProblem = findSentenceProblem(
                ArticleSummaryPrompt.ONE_LINE_FIELD, input, oneLine, MAXIMUM_ONE_LINE_LENGTH)
                .or(() -> findPoliteEndingProblem(ArticleSummaryPrompt.ONE_LINE_FIELD, oneLine));
        oneLineProblem.ifPresent(problems::add);

        // 선택 필드·목록: 걸린 것만 버림
        String whyItMatters = toAcceptedWhyItMatters(input, toSingleLine(brief.whyItMatters()), whatHappened, problems);
        List<AffectedTarget> affectedTargets = toAcceptedAffectedTargets(input, brief.affected(), problems);
        List<String> actions = toAcceptedActions(input, brief.actions(), problems);

        Optional<SummaryProblem> requiredFieldProblem = whatHappenedProblem.or(() -> oneLineProblem);
        if (requiredFieldProblem.isPresent()) {
            return ArticleSummaryValidation.rejected(
                    requiredFieldProblem.get().fieldName() + " " + requiredFieldProblem.get().reason(), problems);
        }
        return ArticleSummaryValidation.accepted(
                new ArticleBrief(oneLine, whatHappened, whyItMatters, affectedTargets, actions), problems);
    }

    private static String toAcceptedWhyItMatters(SourceInput input, String whyItMatters, String whatHappened,
                                                 List<SummaryProblem> problems) {
        if (whyItMatters.isEmpty()) {
            return null;
        }
        Optional<SummaryProblem> problem = findSentenceProblem(
                ArticleSummaryPrompt.WHY_IT_MATTERS_FIELD, input, whyItMatters, MAXIMUM_WHY_IT_MATTERS_LENGTH)
                .or(() -> findRepeatedSentence(whyItMatters, whatHappened).map(repeatedSentence -> new SummaryProblem(
                        ArticleSummaryPrompt.WHY_IT_MATTERS_FIELD, REASON_REPEATS_WHAT_HAPPENED, repeatedSentence)));
        problem.ifPresent(problems::add);
        return problem.isPresent() ? null : whyItMatters;
    }

    // 영향 대상: 이름이 걸리면 항목을 버림, 조건만 걸리면 조건만 버림, 같은 이름은 처음 것만, 최대 4개
    private static List<AffectedTarget> toAcceptedAffectedTargets(SourceInput input, List<AffectedTarget> affectedTargets,
                                                                  List<SummaryProblem> problems) {
        List<AffectedTarget> acceptedTargets = new ArrayList<>();
        for (AffectedTarget affectedTarget : affectedTargets) {
            String target = toSingleLine(affectedTarget.target());
            if (target.isEmpty() || isAlreadyListed(acceptedTargets, target)) {
                continue;
            }
            Optional<SummaryProblem> targetProblem = findTargetProblem(input, target);
            if (targetProblem.isPresent()) {
                problems.add(targetProblem.get());
                continue;
            }
            String detail = toSingleLine(affectedTarget.detail());
            Optional<SummaryProblem> detailProblem =
                    detail.isEmpty() ? Optional.empty() : findDetailProblem(input, detail);
            detailProblem.ifPresent(problems::add);
            if (acceptedTargets.size() < MAXIMUM_AFFECTED_COUNT) {
                acceptedTargets.add(new AffectedTarget(target, detail.isEmpty() || detailProblem.isPresent() ? null : detail));
            }
        }
        return acceptedTargets;
    }

    private static boolean isAlreadyListed(List<AffectedTarget> acceptedTargets, String target) {
        return acceptedTargets.stream().anyMatch(acceptedTarget -> acceptedTarget.target().equalsIgnoreCase(target));
    }

    private static List<String> toAcceptedActions(SourceInput input, List<String> actions, List<SummaryProblem> problems) {
        List<String> acceptedActions = new ArrayList<>();
        for (String rawAction : actions) {
            String action = toSingleLine(rawAction);
            if (action.isEmpty()) {
                continue;
            }
            Optional<SummaryProblem> actionProblem = findSentenceProblem(
                    ArticleSummaryPrompt.ACTIONS_FIELD, input, action, MAXIMUM_ACTION_LENGTH)
                    .or(() -> findPoliteEndingProblem(ArticleSummaryPrompt.ACTIONS_FIELD, action))
                    .map(problem -> withItemFragment(problem, action));
            actionProblem.ifPresent(problems::add);
            if (actionProblem.isEmpty() && acceptedActions.size() < MAXIMUM_ACTION_COUNT) {
                acceptedActions.add(action);
            }
        }
        return acceptedActions;
    }

    // 문장 필드가 걸린 곳 (통과면 빈 값): 한글 필수, 숫자는 제목 + 요약 입력에서 (영어 달 이름·수 낱말을 옮긴 숫자 허용),
    // 옮겨 싣기는 요약 입력에서만 확인 (제목 인용은 허용)
    private static Optional<SummaryProblem> findSentenceProblem(String fieldName, SourceInput input, String sentence,
                                                                int maximumLength) {
        if (sentence.isEmpty()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_EMPTY, null));
        }
        if (sentence.length() > maximumLength) {
            return Optional.of(new SummaryProblem(fieldName, REASON_TOO_LONG, null));
        }
        if (!HANGUL.matcher(sentence).find()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_NO_KOREAN, null));
        }
        return findGroundingProblem(fieldName, input, sentence,
                SourceGroundingPolicy.findUngroundedNumbers(input.titleAndSourceText(), sentence));
    }

    // 무슨 일의 첫 문장이 한 줄 결론과 거의 같은 글이면 걸림 (앱이 둘을 위아래로 이어 보여 줌, 걸린 글 조각: 그 첫 문장)
    private static Optional<SummaryProblem> findRepeatProblem(String whatHappened, String oneLine) {
        String firstSentence = SENTENCE_BOUNDARY.split(whatHappened, 2)[0];
        if (TextSimilarityPolicy.similarity(firstSentence, oneLine) < MINIMUM_REPEAT_SIMILARITY) {
            return Optional.empty();
        }
        return Optional.of(new SummaryProblem(ArticleSummaryPrompt.WHAT_HAPPENED_FIELD, REASON_REPEATS_ONE_LINE, firstSentence));
    }

    // 글의 문장 중 다른 글의 어느 문장과 거의 같은 첫 문장 (없으면 빈 값)
    private static Optional<String> findRepeatedSentence(String text, String otherText) {
        String[] otherSentences = SENTENCE_BOUNDARY.split(otherText);
        for (String sentence : SENTENCE_BOUNDARY.split(text)) {
            for (String otherSentence : otherSentences) {
                if (TextSimilarityPolicy.similarity(sentence, otherSentence) >= MINIMUM_REPEAT_SIMILARITY) {
                    return Optional.of(sentence);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<SummaryProblem> findPoliteEndingProblem(String fieldName, String sentence) {
        return POLITE_SENTENCE_ENDING.matcher(sentence).find()
                ? Optional.empty()
                : Optional.of(new SummaryProblem(fieldName, REASON_NOT_POLITE_SENTENCE, null));
    }

    // 대상 이름이 걸린 곳: 길이, 식별자, 입력에 그 글자대로 적혀 있는지 (대소문자·공백 차이는 무시)
    private static Optional<SummaryProblem> findTargetProblem(SourceInput input, String target) {
        if (target.length() > MAXIMUM_AFFECTED_TARGET_LENGTH) {
            return Optional.of(new SummaryProblem(AFFECTED_TARGET_PROBLEM_FIELD, REASON_TOO_LONG, target));
        }
        if (CVE_ID.matcher(target).matches()) {
            return Optional.of(new SummaryProblem(AFFECTED_TARGET_PROBLEM_FIELD, REASON_IDENTIFIER, target));
        }
        if (!SourceGroundingPolicy.isWrittenInSource(input.titleAndSourceText(), target)) {
            return Optional.of(new SummaryProblem(AFFECTED_TARGET_PROBLEM_FIELD, REASON_NOT_IN_SOURCE, target));
        }
        return Optional.empty();
    }

    // 버전 범위·조건이 걸린 곳: 짧은 값이어야 함, 한글이 없어도 됨 ("< 2.4.1"), 숫자는 옮긴 표기 없이 입력과 글자까지 같아야 함
    private static Optional<SummaryProblem> findDetailProblem(SourceInput input, String detail) {
        if (detail.length() > MAXIMUM_AFFECTED_DETAIL_LENGTH) {
            return Optional.of(new SummaryProblem(AFFECTED_DETAIL_PROBLEM_FIELD, REASON_TOO_LONG, detail));
        }
        if (SENTENCE_ENDING.matcher(detail).find()) {
            return Optional.of(new SummaryProblem(AFFECTED_DETAIL_PROBLEM_FIELD, REASON_SENTENCE, detail));
        }
        return findGroundingProblem(AFFECTED_DETAIL_PROBLEM_FIELD, input, detail,
                SourceGroundingPolicy.findNumbersNotInSource(input.titleAndSourceText(), detail))
                .map(problem -> withItemFragment(problem, detail));
    }

    // 근거 확인 3개 (문장·조건 공통): 글 자체를 가리키는 말 > 입력에 없는 숫자 > 입력 문장 옮겨 싣기
    private static Optional<SummaryProblem> findGroundingProblem(String fieldName, SourceInput input, String generatedText,
                                                                 List<String> ungroundedNumbers) {
        Matcher sourceMention = SOURCE_MENTION.matcher(generatedText);
        if (sourceMention.find()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_MENTIONS_SOURCE, sourceMention.group()));
        }
        if (!ungroundedNumbers.isEmpty()) {
            return Optional.of(new SummaryProblem(fieldName, REASON_NUMBER_NOT_IN_SOURCE,
                    String.join(NUMBER_FRAGMENT_SEPARATOR, ungroundedNumbers)));
        }
        return SourceGroundingPolicy.findCopiedProse(input.sourceText(), generatedText, MINIMUM_VERBATIM_COPY_LENGTH)
                .map(copiedFragment -> new SummaryProblem(fieldName, REASON_COPIES_SOURCE, copiedFragment));
    }

    // 목록 항목은 걸린 글 조각이 없으면 항목 글을 넣음 (고쳐 쓰기가 어느 항목인지 알게)
    private static SummaryProblem withItemFragment(SummaryProblem problem, String itemText) {
        return problem.fragment() == null ? new SummaryProblem(problem.fieldName(), problem.reason(), itemText) : problem;
    }

    // 앞뒤 공백 제거 + 줄바꿈·연속 공백을 한 칸으로 (필드 안 줄바꿈 금지, null 은 빈 값)
    private static String toSingleLine(String text) {
        return text == null ? "" : WHITESPACE_RUN.matcher(text).replaceAll(" ").strip();
    }

    /** 검증 근거 글: 숫자·대상 이름은 제목 + 요약 입력에서, 옮겨 싣기는 요약 입력에서만 확인 */
    private record SourceInput(String title, String sourceText) {

        private String titleAndSourceText() {
            return title + " " + sourceText;
        }
    }
}
