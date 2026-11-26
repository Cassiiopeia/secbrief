package kr.suhsaechan.secbrief.web.admin.support;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import org.springframework.stereotype.Component;

/**
 * 관리자 화면 시각 표기 단일 지점: 시각 > 서울 yyyy-MM-dd HH:mm (없으면 -)
 * 템플릿에서 ${@adminDateTimeFormatter.format(시각)} 으로 씀
 */
@Component("adminDateTimeFormatter")
public class AdminDateTimeFormatter {

    private static final String EMPTY_TEXT = "-";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SeoulCalendar.SEOUL);

    public String format(Instant instant) {
        return instant == null ? EMPTY_TEXT : DATE_TIME_FORMATTER.format(instant);
    }
}
