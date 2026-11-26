package kr.suhsaechan.secbrief.advisory.indicator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * 해외 지표 응답 > 단계 값: CIS 텍스트 피드는 첫 줄, SANS InfoCon 은 JSON status
 * 소문자·앞뒤 공백 정리 후 지표의 단계 목록 안 값만 (그 밖·형식 오류는 비어 있음)
 */
public final class InternationalAlertIndicatorParser {

    private static final String SANS_STATUS_FIELD = "status";

    private InternationalAlertIndicatorParser() {
    }

    public static Optional<String> toLevelKey(InternationalAlertIndicator indicator, byte[] responseBody) {
        Optional<String> rawLevel = switch (indicator) {
            case CIS_MS_ISAC -> readFirstLine(responseBody);
            case SANS_ISC_INFOCON -> readSansStatus(responseBody);
        };
        return rawLevel.map(level -> level.strip().toLowerCase(Locale.ROOT))
                .filter(level -> indicator.getLevelKeys().contains(level));
    }

    private static Optional<String> readFirstLine(byte[] responseBody) {
        return new String(responseBody, UTF_8).lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst();
    }

    private static Optional<String> readSansStatus(byte[] responseBody) {
        try {
            String status = OBJECT_MAPPER.readTree(responseBody).path(SANS_STATUS_FIELD).asText("");
            return status.isBlank() ? Optional.empty() : Optional.of(status);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
