package kr.suhsaechan.secbrief.advisory.policy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * AI 가 쓴 글이 원문에 근거하는지 확인 (순수 함수): 원문에 없는 숫자 표기, 원문 문장 그대로 옮기기
 * 제목 번역·키워드 검증(ArticleTitleAnalysisPolicy)과 요약 검증이 함께 씀 (버전·건수·날짜 지어내기 방지)
 * 요약 검증 전용 2개: 옮긴 숫자 허용(findUngroundedNumbers), 이름·식별자 나열을 뺀 옮겨 싣기(findCopiedProse)
 */
public final class SourceGroundingPolicy {

    // 숫자 표기 1개: 숫자열을 . 또는 , 로 이은 묶음 (2.4.1, 9.8, 1,200, 2026). 묶음째 비교해 2.4.0 의 4 로 "4개"가 통과하지 않게 함
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");
    // 한국어 달 표기의 숫자 (10월 의 10): 버전·소수 일부(2.10월)는 제외
    private static final Pattern KOREAN_MONTH_NUMBER = Pattern.compile("(?<![\\d.,])(1[0-2]|[1-9])(?=월)");
    // 영어 달 이름 (순서 = 달 숫자 - 1): 전체 이름 또는 앞 3글자 줄임 (September 는 Sept 도)
    private static final List<Pattern> ENGLISH_MONTH_NAMES = List.of(
            Pattern.compile("\\b(January|Jan)\\b"), Pattern.compile("\\b(February|Feb)\\b"),
            Pattern.compile("\\b(March|Mar)\\b"), Pattern.compile("\\b(April|Apr)\\b"),
            Pattern.compile("\\bMay\\b"), Pattern.compile("\\b(June|Jun)\\b"),
            Pattern.compile("\\b(July|Jul)\\b"), Pattern.compile("\\b(August|Aug)\\b"),
            Pattern.compile("\\b(September|Sept|Sep)\\b"), Pattern.compile("\\b(October|Oct)\\b"),
            Pattern.compile("\\b(November|Nov)\\b"), Pattern.compile("\\b(December|Dec)\\b"));
    // 영어 수 낱말 (순서 = 숫자, zero ~ twelve): 이보다 큰 수·어림수(dozens)는 숫자로 옮기지 않음
    private static final List<Pattern> ENGLISH_NUMBER_WORDS = Stream.of("zero", "one", "two", "three", "four", "five", "six",
                    "seven", "eight", "nine", "ten", "eleven", "twelve")
            .map(numberWord -> Pattern.compile("\\b" + numberWord + "\\b", Pattern.CASE_INSENSITIVE))
            .toList();
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    // 소문자만으로 된 영어 낱말 (문장을 이루는 낱말: 제품 이름·버전·식별자에는 드묾)
    private static final Pattern LOWERCASE_ENGLISH_WORD = Pattern.compile("(?<![A-Za-z0-9_./:@-])[a-z]{2,}(?![A-Za-z0-9_/:@-])");
    // 한글 없는 같은 구간을 문장으로 보는 소문자 낱말 수 (이름 사이의 for·and 한두 개는 문장이 아님)
    private static final int MINIMUM_PROSE_WORD_COUNT = 4;

    private SourceGroundingPolicy() {
    }

    /**
     * 쓴 글의 숫자 표기 중 원문에 같은 표기가 없는 것이 하나라도 있는지 (2.4.0 > 2.4.1, 3개 > 4개, 09 > 9 같은 변경도 걸림)
     */
    public static boolean hasNumberNotInSource(String sourceText, String generatedText) {
        return !findNumbersNotInSource(sourceText, generatedText).isEmpty();
    }

    /**
     * 쓴 글의 숫자 표기 중 원문에 없는 것 (나온 순서, 중복 제거): 고쳐 쓰기 요청에 그대로 알려 줌
     */
    public static List<String> findNumbersNotInSource(String sourceText, String generatedText) {
        Set<String> sourceNumberTokens = toNumberTokens(sourceText);
        List<String> numbersNotInSource = new ArrayList<>();
        Matcher matcher = NUMBER_TOKEN.matcher(generatedText);
        while (matcher.find()) {
            String numberToken = matcher.group();
            if (!sourceNumberTokens.contains(numberToken) && !numbersNotInSource.contains(numberToken)) {
                numbersNotInSource.add(numberToken);
            }
        }
        return numbersNotInSource;
    }

    /**
     * 쓴 글에 원문과 글자가 똑같은 구간이 minimumCopyLength 자 이상 있는지 (공백 차이는 무시)
     */
    public static boolean hasVerbatimCopy(String sourceText, String generatedText, int minimumCopyLength) {
        String collapsedSource = toCollapsed(sourceText);
        String collapsedGenerated = toCollapsed(generatedText);
        for (int startIndex = 0; startIndex + minimumCopyLength <= collapsedGenerated.length(); startIndex++) {
            if (collapsedSource.contains(collapsedGenerated.substring(startIndex, startIndex + minimumCopyLength))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 원문에서 옮겨 실은 글 (minimumCopyLength 자 이상 똑같은 구간 중 문장인 첫 구간, 같은 데까지 늘린 값), 없으면 빈 값
     * 문장: 한글이 있거나 소문자 영어 낱말이 4개 이상. 제품 이름·버전 나열·식별자(CVSS 벡터)는 그대로 적어야 하는 값이라 제외
     */
    public static Optional<String> findCopiedProse(String sourceText, String generatedText, int minimumCopyLength) {
        String collapsedSource = toCollapsed(sourceText);
        String collapsedGenerated = toCollapsed(generatedText);
        for (int startIndex = 0; startIndex + minimumCopyLength <= collapsedGenerated.length(); startIndex++) {
            int endIndex = startIndex + minimumCopyLength;
            if (!collapsedSource.contains(collapsedGenerated.substring(startIndex, endIndex))) {
                continue;
            }
            while (endIndex < collapsedGenerated.length()
                    && collapsedSource.contains(collapsedGenerated.substring(startIndex, endIndex + 1))) {
                endIndex++;
            }
            String identicalStretch = collapsedGenerated.substring(startIndex, endIndex);
            if (isProse(identicalStretch)) {
                return Optional.of(identicalStretch);
            }
        }
        return Optional.empty();
    }

    /**
     * 쓴 글의 숫자 표기 중 원문에 근거가 없는 것 (나온 순서, 중복 제거)
     * 근거 있음 3가지: 원문에 같은 표기, 원문의 영어 달 이름을 옮긴 "N월" (October > 10월), 원문의 영어 수 낱말을 옮긴 숫자 (six > 6, zero ~ twelve)
     */
    public static List<String> findUngroundedNumbers(String sourceText, String generatedText) {
        return findNumbersNotInSource(sourceText, toTextWithoutTranslatedMonthNumbers(sourceText, generatedText)).stream()
                .filter(numberToken -> !isTranslatedNumberWord(sourceText, numberToken))
                .toList();
    }

    // 쓴 글의 "N월" 중 원문에 그 달의 영어 이름이 있는 것 > 숫자를 뺀 "월" (그 밖의 글은 그대로, 날짜의 일·연도는 그대로 검사)
    private static String toTextWithoutTranslatedMonthNumbers(String sourceText, String generatedText) {
        return KOREAN_MONTH_NUMBER.matcher(generatedText).replaceAll(monthNumberMatch -> {
            int monthNumber = Integer.parseInt(monthNumberMatch.group());
            return ENGLISH_MONTH_NAMES.get(monthNumber - 1).matcher(sourceText).find() ? "" : monthNumberMatch.group();
        });
    }

    // 0 ~ 12 한 덩어리 숫자이고 원문에 그 수의 영어 낱말이 있음 (2.4 의 2 처럼 묶음의 일부는 해당 없음)
    private static boolean isTranslatedNumberWord(String sourceText, String numberToken) {
        if (numberToken.length() > 2 || !numberToken.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int number = Integer.parseInt(numberToken);
        return number < ENGLISH_NUMBER_WORDS.size() && ENGLISH_NUMBER_WORDS.get(number).matcher(sourceText).find();
    }

    private static boolean isProse(String identicalStretch) {
        return HANGUL.matcher(identicalStretch).find()
                || LOWERCASE_ENGLISH_WORD.matcher(identicalStretch).results().count() >= MINIMUM_PROSE_WORD_COUNT;
    }

    private static Set<String> toNumberTokens(String text) {
        Set<String> numberTokens = new HashSet<>();
        Matcher matcher = NUMBER_TOKEN.matcher(text);
        while (matcher.find()) {
            numberTokens.add(matcher.group());
        }
        return numberTokens;
    }

    private static String toCollapsed(String text) {
        return WHITESPACE_RUN.matcher(text).replaceAll(" ").strip();
    }
}
