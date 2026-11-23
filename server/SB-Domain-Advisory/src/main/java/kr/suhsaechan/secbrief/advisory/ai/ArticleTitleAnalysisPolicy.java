package kr.suhsaechan.secbrief.advisory.ai;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;

/**
 * AI 번역 검증: 원문에 없는 숫자열이 생기면 버림 (버전·건수 지어내기 방지)
 * 버리는 조건 5개: 국내 항목, 빈 번역, 500자 초과, 원문에 없는 숫자열, 원문과 같음
 */
public final class ArticleTitleAnalysisPolicy {

    // article.translated_title 열 길이
    public static final int MAXIMUM_TRANSLATED_TITLE_LENGTH = 500;
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    private ArticleTitleAnalysisPolicy() {
    }

    /**
     * 받아들일 번역 (trim) 또는 null
     */
    public static String toAcceptedTranslation(String originalTitle, ArticleRegion region, String translatedTitle) {
        if (region != ArticleRegion.OVERSEAS || translatedTitle == null) {
            return null;
        }
        String trimmedTranslation = translatedTitle.strip();
        if (trimmedTranslation.isEmpty()
                || trimmedTranslation.length() > MAXIMUM_TRANSLATED_TITLE_LENGTH
                || trimmedTranslation.equals(originalTitle.strip())
                || hasDigitsNotInOriginal(originalTitle, trimmedTranslation)) {
            return null;
        }
        return trimmedTranslation;
    }

    // 번역의 모든 숫자열이 원문 숫자열 집합 안에 있어야 함 (2.4.1 > 2.4.0 같은 변경도 걸림)
    private static boolean hasDigitsNotInOriginal(String originalTitle, String translatedTitle) {
        Set<String> originalDigitRuns = toDigitRuns(originalTitle);
        return !originalDigitRuns.containsAll(toDigitRuns(translatedTitle));
    }

    private static Set<String> toDigitRuns(String text) {
        Set<String> digitRuns = new HashSet<>();
        Matcher matcher = DIGIT_RUN.matcher(text);
        while (matcher.find()) {
            digitRuns.add(matcher.group());
        }
        return digitRuns;
    }
}
