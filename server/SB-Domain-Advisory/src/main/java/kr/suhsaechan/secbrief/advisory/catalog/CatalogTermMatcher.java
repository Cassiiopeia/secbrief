package kr.suhsaechan.secbrief.advisory.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 분류 낱말 일치: "a, b, ~c" > 낱말 목록 (~ 는 약한 낱말), 글 > 가장 긴 일치 낱말 길이
 * 영문·숫자 낱말은 앞뒤가 영문·숫자가 아니어야 일치 (git 이 github 에 맞지 않게), 한글이 든 낱말은 부분 일치 (조사가 붙어도)
 */
public final class CatalogTermMatcher {

    public static final String TERM_DELIMITER = ",";
    public static final String WEAK_TERM_PREFIX = "~";
    // 일치 없음
    public static final int NO_MATCH = 0;

    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");

    private final List<CompiledTerm> strongTerms;
    private final List<CompiledTerm> weakTerms;

    private CatalogTermMatcher(List<CompiledTerm> strongTerms, List<CompiledTerm> weakTerms) {
        this.strongTerms = strongTerms;
        this.weakTerms = weakTerms;
    }

    /** 낱말 글 > 일치기 (빈 낱말·공백은 버림, 소문자로) */
    public static CatalogTermMatcher of(String matchTerms) {
        List<CompiledTerm> strongTerms = new ArrayList<>();
        List<CompiledTerm> weakTerms = new ArrayList<>();
        for (String rawTerm : (matchTerms == null ? "" : matchTerms).split(TERM_DELIMITER)) {
            String term = rawTerm.strip().toLowerCase(Locale.ROOT);
            boolean isWeak = term.startsWith(WEAK_TERM_PREFIX);
            String bareTerm = isWeak ? term.substring(WEAK_TERM_PREFIX.length()).strip() : term;
            if (bareTerm.isEmpty()) {
                continue;
            }
            (isWeak ? weakTerms : strongTerms).add(new CompiledTerm(bareTerm, toPattern(bareTerm)));
        }
        return new CatalogTermMatcher(List.copyOf(strongTerms), List.copyOf(weakTerms));
    }

    /** 강한 낱말 중 글에 든 가장 긴 낱말 길이 (없으면 0) */
    public int findLongestStrongMatchLength(String lowerCaseText) {
        return findLongestMatchLength(strongTerms, lowerCaseText);
    }

    /** 약한 낱말 중 글에 든 가장 긴 낱말 길이 (없으면 0) */
    public int findLongestWeakMatchLength(String lowerCaseText) {
        return findLongestMatchLength(weakTerms, lowerCaseText);
    }

    /** 원문 값이 낱말 하나와 같은지 (대소문자 무시, GitHub ecosystem 값 비교) */
    public boolean hasExactTerm(String value) {
        if (value == null) {
            return false;
        }
        String lowerCaseValue = value.strip().toLowerCase(Locale.ROOT);
        return strongTerms.stream().anyMatch(term -> term.text().equals(lowerCaseValue));
    }

    public boolean isEmpty() {
        return strongTerms.isEmpty() && weakTerms.isEmpty();
    }

    private static int findLongestMatchLength(List<CompiledTerm> terms, String lowerCaseText) {
        if (lowerCaseText == null || lowerCaseText.isEmpty()) {
            return NO_MATCH;
        }
        int longestLength = NO_MATCH;
        for (CompiledTerm term : terms) {
            if (term.text().length() > longestLength && term.pattern().matcher(lowerCaseText).find()) {
                longestLength = term.text().length();
            }
        }
        return longestLength;
    }

    // 한글 낱말은 부분 일치, 그 밖은 앞뒤 영문·숫자 경계
    private static Pattern toPattern(String term) {
        if (HANGUL.matcher(term).find()) {
            return Pattern.compile(Pattern.quote(term));
        }
        return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(term) + "(?![a-z0-9])");
    }

    private record CompiledTerm(String text, Pattern pattern) {
    }
}
