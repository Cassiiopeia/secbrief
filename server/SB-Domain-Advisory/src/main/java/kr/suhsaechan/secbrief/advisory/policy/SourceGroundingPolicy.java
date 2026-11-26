package kr.suhsaechan.secbrief.advisory.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 가 쓴 글이 원문에 근거하는지 확인 (순수 함수): 원문에 없는 숫자 표기, 원문 문장 그대로 옮기기
 * 제목 번역·키워드 검증(ArticleTitleAnalysisPolicy)과 요약 검증이 함께 씀 (버전·건수·날짜 지어내기 방지)
 */
public final class SourceGroundingPolicy {

    // 숫자 표기 1개: 숫자열을 . 또는 , 로 이은 묶음 (2.4.1, 9.8, 1,200, 2026). 묶음째 비교해 2.4.0 의 4 로 "4개"가 통과하지 않게 함
    private static final Pattern NUMBER_TOKEN = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");

    private SourceGroundingPolicy() {
    }

    /**
     * 쓴 글의 숫자 표기 중 원문에 같은 표기가 없는 것이 하나라도 있는지 (2.4.0 > 2.4.1, 3개 > 4개, 09 > 9 같은 변경도 걸림)
     */
    public static boolean hasNumberNotInSource(String sourceText, String generatedText) {
        return !toNumberTokens(sourceText).containsAll(toNumberTokens(generatedText));
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
