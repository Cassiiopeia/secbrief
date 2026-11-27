package kr.suhsaechan.secbrief.advisory.policy;

import java.util.Locale;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.common.text.TextTruncation;
import org.springframework.web.util.HtmlUtils;

/**
 * AI 요약 입력 정리: 출처가 준 요약문·공개 설명 > HTML 을 벗긴 글 (요약이 끝나면 지우는 임시 값)
 * 없는 것으로 보는 조건 3개: 비어 있음, 주소를 뺀 글이 40자 미만, 제목에 이미 들어 있는 글
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
    // 주소는 내용으로 세지 않음 (Debian 권고 피드는 요약문 자리에 추적 페이지 주소만 줌: 요약할 사실이 없음)
    private static final Pattern WEB_ADDRESS = Pattern.compile("https?://\\S+");

    private SourceTextPolicy() {
    }

    /**
     * 정리한 요약 입력 또는 null: 코드 블록·태그 제거 > HTML 문자 표기 풀기 > 공백 정리 > 길이 제한 > 쓸 만한지 확인
     */
    public static String toSourceText(String rawSourceText, String cleanedTitle) {
        String sourceText = toCleanedText(rawSourceText);
        return isWorthSummarizing(sourceText, cleanedTitle) ? sourceText : null;
    }

    /**
     * 정리만 한 글 (쓸 만한지 확인 없음, null 은 null): 다시 받은 입력이 내용 없는 입력인지 따로 셀 때 (로컬 적재 도구)
     */
    public static String toCleanedText(String rawSourceText) {
        if (rawSourceText == null) {
            return null;
        }
        String withoutCodeBlocks = SCRIPT_OR_STYLE_BLOCK.matcher(rawSourceText).replaceAll(" ");
        String withoutTags = HTML_TAG.matcher(withoutCodeBlocks).replaceAll(" ");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        String collapsed = WHITESPACE_OR_CONTROL_RUN.matcher(unescaped).replaceAll(" ").strip();
        return truncate(collapsed);
    }

    /**
     * 요약할 내용이 있는 입력인지: 주소를 뺀 글이 40자 이상이고 제목에 통째로 들어 있지 않음 (NVD 짧은 설명은 제목 그 자체)
     * 수집 정리와 요약 직전 확인이 함께 씀 (이 규칙 이전에 저장된 대기 행도 제공자를 부르지 않고 거름)
     */
    public static boolean isWorthSummarizing(String sourceText, String cleanedTitle) {
        if (sourceText == null) {
            return false;
        }
        String textWithoutWebAddresses = WEB_ADDRESS.matcher(sourceText).replaceAll(" ").strip();
        return textWithoutWebAddresses.length() >= MINIMUM_SOURCE_TEXT_LENGTH
                && !cleanedTitle.toLowerCase(Locale.ROOT).contains(sourceText.toLowerCase(Locale.ROOT));
    }

    // 길이 제한 (잘랐을 때만 끝 공백 정리)
    private static String truncate(String text) {
        if (text.length() <= MAXIMUM_SOURCE_TEXT_LENGTH) {
            return text;
        }
        return TextTruncation.truncateKeepingSurrogatePairs(text, MAXIMUM_SOURCE_TEXT_LENGTH).strip();
    }
}
