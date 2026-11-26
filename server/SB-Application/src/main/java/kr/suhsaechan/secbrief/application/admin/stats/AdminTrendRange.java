package kr.suhsaechan.secbrief.application.admin.stats;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 추이 조회 범위: 화면 값(unit, from, to) > 보정된 단위·기간 (서울 날짜, 양 끝 포함)
 * 기본: 일별 최근 30일, 월별 최근 12달 / 틀린 날짜는 기본값, 뒤집힌 순서는 맞바꿈, 미래는 오늘까지, 최대 366일
 */
public record AdminTrendRange(AdminTrendUnit unit, LocalDate from, LocalDate to) {

    // 일별 기본 기간 (일)
    public static final int DEFAULT_DAY_COUNT = 30;
    // 월별 기본 칸 수 (달)
    public static final int DEFAULT_MONTH_COUNT = 12;
    // 조회 기간 상한 (일): 넘으면 끝 날짜 기준으로 앞을 자름
    public static final int MAXIMUM_DAY_COUNT = 366;

    public static AdminTrendRange of(String unitParameter, String fromParameter, String toParameter, LocalDate today) {
        AdminTrendUnit unit = AdminTrendUnit.fromParameter(unitParameter);
        LocalDate to = parseOrNull(toParameter);
        if (to == null || to.isAfter(today)) {
            to = today;
        }
        LocalDate from = parseOrNull(fromParameter);
        if (from == null) {
            from = unit == AdminTrendUnit.MONTH
                    ? to.withDayOfMonth(1).minusMonths(DEFAULT_MONTH_COUNT - 1L)
                    : to.minusDays(DEFAULT_DAY_COUNT - 1L);
        }
        if (from.isAfter(to)) {
            LocalDate later = from.isAfter(today) ? today : from;
            from = to;
            to = later;
        }
        LocalDate earliestFrom = to.minusDays(MAXIMUM_DAY_COUNT - 1L);
        if (from.isBefore(earliestFrom)) {
            from = earliestFrom;
        }
        return new AdminTrendRange(unit, from, to);
    }

    /**
     * 캐시 키 조각 (unit|from|to)
     */
    public String cacheKey() {
        return unit.getSqlName() + "|" + from + "|" + to;
    }

    private static LocalDate parseOrNull(String dateParameter) {
        if (dateParameter == null || dateParameter.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateParameter.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
