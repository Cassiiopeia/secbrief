package kr.suhsaechan.secbrief.advisory.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.policy.SourceGroundingPolicy;

/**
 * AI 결과 검증 (번역, 회사·제품 이름, 키워드): 원문에 없는 숫자 표기가 생기면 버림 (버전·건수 지어내기 방지)
 * 번역을 버리는 조건 5개: 국내 항목, 빈 번역, 500자 초과, 원문에 없는 숫자 표기, 원문과 같음
 */
public final class ArticleTitleAnalysisPolicy {

    // article.translated_title 열 길이
    public static final int MAXIMUM_TRANSLATED_TITLE_LENGTH = 500;
    // article.subject_name 열 길이
    public static final int MAXIMUM_SUBJECT_NAME_LENGTH = 80;
    // article_keyword.keyword 열 길이
    public static final int MAXIMUM_KEYWORD_LENGTH = 40;
    // 기사 1건의 키워드 최대 개수
    public static final int MAXIMUM_KEYWORD_COUNT = 5;
    private static final String HASHTAG_SIGN = "#";
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
    // CVE 번호 모양 (예: CVE-2026-1234): 키워드로 쓰지 않음
    private static final Pattern CVE_ID = Pattern.compile("(?i)CVE-\\d{4}-\\d+");

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
                || SourceGroundingPolicy.hasNumberNotInSource(originalTitle, trimmedTranslation)) {
            return null;
        }
        return trimmedTranslation;
    }

    /**
     * 받아들일 회사·제품 이름 (trim) 또는 null: 원문 제목에 그대로 있는 글자만 (대소문자 무시), 80자 이내
     * 제목에 없는 이름은 AI 가 지어낸 것으로 보고 버림
     */
    public static String toAcceptedSubjectName(String originalTitle, String subjectName) {
        if (subjectName == null) {
            return null;
        }
        String trimmedSubjectName = subjectName.strip();
        if (trimmedSubjectName.isEmpty()
                || trimmedSubjectName.length() > MAXIMUM_SUBJECT_NAME_LENGTH
                || !originalTitle.toLowerCase(Locale.ROOT).contains(trimmedSubjectName.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return trimmedSubjectName;
    }

    /**
     * 받아들일 키워드 목록 (AI 가 준 순서, 최대 5개): # 제거 > 공백 정리 > 불량 제외 > 중복 제외 (대소문자 무시, 먼저 나온 표기 유지)
     * 버리는 조건 4개: 빈 값, 40자 초과, 원문 제목에 없는 숫자 표기, CVE 번호
     */
    public static List<String> toAcceptedKeywords(String originalTitle, List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        List<String> acceptedKeywords = new ArrayList<>();
        Set<String> acceptedLowerCaseKeywords = new HashSet<>();
        for (String keyword : keywords) {
            if (acceptedKeywords.size() >= MAXIMUM_KEYWORD_COUNT) {
                break;
            }
            String cleanedKeyword = toCleanedKeyword(keyword);
            if (isAcceptableKeyword(originalTitle, cleanedKeyword)
                    && acceptedLowerCaseKeywords.add(cleanedKeyword.toLowerCase(Locale.ROOT))) {
                acceptedKeywords.add(cleanedKeyword);
            }
        }
        return List.copyOf(acceptedKeywords);
    }

    // # 제거 > 연속 공백 하나로 > trim (null 은 빈 문자열)
    private static String toCleanedKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        return WHITESPACE_RUN.matcher(keyword.replace(HASHTAG_SIGN, "")).replaceAll(" ").strip();
    }

    private static boolean isAcceptableKeyword(String originalTitle, String cleanedKeyword) {
        return !cleanedKeyword.isEmpty()
                && cleanedKeyword.length() <= MAXIMUM_KEYWORD_LENGTH
                && !CVE_ID.matcher(cleanedKeyword).find()
                && !SourceGroundingPolicy.hasNumberNotInSource(originalTitle, cleanedKeyword);
    }

}
