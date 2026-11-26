package kr.suhsaechan.secbrief.advisory.policy;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * AI 요약 입력 정리: 출처가 준 요약문·공개 설명 > HTML 을 벗긴 글 (요약이 끝나면 지우는 임시 값)
 * 없는 것으로 보는 조건 3개: 비어 있음, 40자 미만, 제목에 이미 들어 있는 글
 */
public final class SourceTextPolicy {

    // 요약 입력 최대 길이 (AI 입력 토큰·보관량 상한, 넘으면 앞부분만)
    public static final int MAXIMUM_SOURCE_TEXT_LENGTH = 6000;
    // 이보다 짧으면 제목 이상의 내용이 없다고 봄
    public static final int MINIMUM_SOURCE_TEXT_LENGTH = 40;

    // 통째로 버리는 블록 (글이 아닌 코드)
    private static final Pattern SCRIPT_OR_STYLE_BLOCK =
            Pattern.compile("<(script|style)\\b[^>]*>.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // 태그는 공백으로 바꿈 (<p>가</p><p>나</p> 가 붙지 않게)
    private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    // 일반 공백 + 줄바꿈 + 줄바꿈 없는 공백(NBSP) + 제어문자
    private static final Pattern WHITESPACE_OR_CONTROL_RUN = Pattern.compile("[\\s\\u00A0\\p{Cc}]+");

    private SourceTextPolicy() {
    }

    /**
     * 정리한 요약 입력 또는 null: 코드 블록·태그 제거 > HTML 문자 표기 풀기 > 공백 정리 > 길이 제한 > 쓸 만한지 확인
     */
    public static String toSourceText(String rawSourceText, String cleanedTitle) {
        if (rawSourceText == null) {
            return null;
        }
        String withoutCodeBlocks = SCRIPT_OR_STYLE_BLOCK.matcher(rawSourceText).replaceAll(" ");
        String withoutTags = HTML_TAG.matcher(withoutCodeBlocks).replaceAll(" ");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        String collapsed = WHITESPACE_OR_CONTROL_RUN.matcher(unescaped).replaceAll(" ").strip();
        String sourceText = truncate(collapsed);
        return isWorthSummarizing(sourceText, cleanedTitle) ? sourceText : null;
    }

    // 40자 이상이고 제목에 통째로 들어 있지 않음 (NVD 짧은 설명은 제목 그 자체)
    private static boolean isWorthSummarizing(String sourceText, String cleanedTitle) {
        return sourceText.length() >= MINIMUM_SOURCE_TEXT_LENGTH
                && !cleanedTitle.toLowerCase(Locale.ROOT).contains(sourceText.toLowerCase(Locale.ROOT));
    }

    // 길이 제한 (잘린 끝이 서로게이트 쌍 절반이면 그 글자는 뺌: DB 인코딩 오류 방지)
    private static String truncate(String text) {
        if (text.length() <= MAXIMUM_SOURCE_TEXT_LENGTH) {
            return text;
        }
        int endIndex = Character.isHighSurrogate(text.charAt(MAXIMUM_SOURCE_TEXT_LENGTH - 1))
                ? MAXIMUM_SOURCE_TEXT_LENGTH - 1
                : MAXIMUM_SOURCE_TEXT_LENGTH;
        return text.substring(0, endIndex).strip();
    }
}
