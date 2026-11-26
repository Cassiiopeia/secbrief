package kr.suhsaechan.secbrief.application.admin.stats;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 추이 묶음 단위: 일별, 월별 (sqlName 은 date_trunc 첫 인자이자 응답 unit 값)
 */
@Getter
@RequiredArgsConstructor
public enum AdminTrendUnit {
    DAY("day", DateTimeFormatter.ofPattern("M/d")),
    MONTH("month", DateTimeFormatter.ofPattern("yyyy-MM"));

    private final String sqlName;
    private final DateTimeFormatter labelFormatter;

    /**
     * 화면 값 > 단위 (month 만 월별, 그 밖의 값·빈 값은 일별)
     */
    public static AdminTrendUnit fromParameter(String parameter) {
        return parameter != null && MONTH.sqlName.equalsIgnoreCase(parameter.trim()) ? MONTH : DAY;
    }

    /**
     * 날짜가 속한 칸의 첫날 (월별이면 그달 1일)
     */
    public LocalDate bucketStartOf(LocalDate date) {
        return this == MONTH ? date.withDayOfMonth(1) : date;
    }

    public LocalDate nextBucketStart(LocalDate bucketStart) {
        return this == MONTH ? bucketStart.plusMonths(1) : bucketStart.plusDays(1);
    }

    /**
     * 기간 안의 칸 첫날 목록 (from 이 속한 칸부터 to 가 속한 칸까지, 빈 칸 포함)
     */
    public List<LocalDate> bucketStartsBetween(LocalDate from, LocalDate to) {
        List<LocalDate> bucketStarts = new ArrayList<>();
        for (LocalDate bucketStart = bucketStartOf(from); !bucketStart.isAfter(to); bucketStart = nextBucketStart(bucketStart)) {
            bucketStarts.add(bucketStart);
        }
        return bucketStarts;
    }

    /**
     * 칸 첫날 > x축 이름 (일별 11/26, 월별 2026-11)
     */
    public String labelOf(LocalDate bucketStart) {
        return labelFormatter.format(bucketStart);
    }
}
