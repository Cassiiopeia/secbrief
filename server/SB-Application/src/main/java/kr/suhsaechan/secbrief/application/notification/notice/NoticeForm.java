package kr.suhsaechan.secbrief.application.notification.notice;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 공지사항 입력: 제목, 본문(줄바꿈은 LF 로 맞춤), 선택 링크, 고정 여부
 */
public record NoticeForm(String title, String body, String linkUrl, boolean isPinned) {

    public static final int MAXIMUM_TITLE_LENGTH = 100;
    public static final int MAXIMUM_BODY_LENGTH = 5000;
    // notification.link_url 열 길이
    public static final int MAXIMUM_LINK_URL_LENGTH = 1000;
    // 브라우저 textarea 가 보내는 CRLF·CR 줄바꿈 (앱이 줄마다 \r 을 그리지 않게 LF 로, #87)
    private static final Pattern CARRIAGE_RETURN_LINE_BREAK = Pattern.compile("\r\n?");
    private static final String LINE_FEED = "\n";

    /**
     * 정리 + 검증: 앞뒤 공백 제거, 본문 줄바꿈 LF 로, 빈 링크는 없음으로 > 길이(제목 1~100, 본문 1~5000) > 링크는 http·https 절대 주소만
     */
    public NoticeForm toValidated() {
        String trimmedTitle = title == null ? "" : title.strip();
        String trimmedBody = body == null ? "" : CARRIAGE_RETURN_LINE_BREAK.matcher(body).replaceAll(LINE_FEED).strip();
        String trimmedLinkUrl = linkUrl == null || linkUrl.isBlank() ? null : linkUrl.strip();
        if (trimmedTitle.isEmpty() || trimmedTitle.length() > MAXIMUM_TITLE_LENGTH) {
            throw new CustomException(ErrorCode.NOTICE_TITLE_LENGTH);
        }
        if (trimmedBody.isEmpty() || trimmedBody.length() > MAXIMUM_BODY_LENGTH) {
            throw new CustomException(ErrorCode.NOTICE_BODY_LENGTH);
        }
        if (trimmedLinkUrl != null && !isHttpUrl(trimmedLinkUrl)) {
            throw new CustomException(ErrorCode.NOTICE_LINK_URL_INVALID);
        }
        return new NoticeForm(trimmedTitle, trimmedBody, trimmedLinkUrl, isPinned);
    }

    private static boolean isHttpUrl(String url) {
        if (url.length() > MAXIMUM_LINK_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
