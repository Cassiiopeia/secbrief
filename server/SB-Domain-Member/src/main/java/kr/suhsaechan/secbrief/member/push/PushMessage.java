package kr.suhsaechan.secbrief.member.push;

import java.util.Map;

/**
 * 푸시 1건: 받는 토큰, 제목, 본문, 앱이 읽는 부가 값(data), Android 채널
 */
public record PushMessage(String pushToken, String title, String body, Map<String, String> data, String channelId) {
}
