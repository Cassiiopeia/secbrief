package kr.suhsaechan.secbrief.common.text;

/**
 * 글자 수 제한 단일 지점: 잘린 끝이 서로게이트 쌍 절반이면 그 글자는 뺌 (DB 인코딩 오류 방지)
 */
public final class TextTruncation {

    private TextTruncation() {
    }

    /**
     * 최대 길이 이하로 자름 (짧으면 그대로, 이모지 같은 2칸 글자를 반으로 가르지 않음)
     */
    public static String truncateKeepingSurrogatePairs(String text, int maximumLength) {
        if (text.length() <= maximumLength) {
            return text;
        }
        int endIndex = Character.isHighSurrogate(text.charAt(maximumLength - 1)) ? maximumLength - 1 : maximumLength;
        return text.substring(0, endIndex);
    }
}
