package kr.suhsaechan.secbrief.advisory.policy;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 두 한국어 글이 거의 같은 글인지 (순수 함수): 한글 2글자 묶음의 겹침 비율 (다이스 계수, 0 ~ 1)
 * 한글만 비교: 제품 이름·버전·영어 낱말은 두 글에 똑같이 나와도 되풀이가 아님 (긴 이름이 겹침 비율을 부풀리지 않게)
 */
public final class TextSimilarityPolicy {

    private static final Pattern NOT_HANGUL = Pattern.compile("[^가-힣]+");
    // 글자 묶음 길이 (한국어는 2글자 묶음이 낱말 단위에 가까움)
    private static final int GRAM_LENGTH = 2;

    private TextSimilarityPolicy() {
    }

    /**
     * 겹침 비율: 2 × 공통 묶음 수 ÷ (묶음 수의 합). 어느 한쪽에 한글이 2글자 미만이면 0
     */
    public static double similarity(String firstText, String secondText) {
        Set<String> firstGrams = toHangulGrams(firstText);
        Set<String> secondGrams = toHangulGrams(secondText);
        if (firstGrams.isEmpty() || secondGrams.isEmpty()) {
            return 0;
        }
        long commonGramCount = firstGrams.stream().filter(secondGrams::contains).count();
        return 2.0 * commonGramCount / (firstGrams.size() + secondGrams.size());
    }

    private static Set<String> toHangulGrams(String text) {
        String hangul = NOT_HANGUL.matcher(text).replaceAll("");
        Set<String> grams = new HashSet<>();
        for (int startIndex = 0; startIndex + GRAM_LENGTH <= hangul.length(); startIndex++) {
            grams.add(hangul.substring(startIndex, startIndex + GRAM_LENGTH));
        }
        return grams;
    }
}
