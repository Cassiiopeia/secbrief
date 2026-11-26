package kr.suhsaechan.secbrief.application.admin.usage;

import java.time.LocalDate;

/**
 * 이용 집계 1칸: 서울 날짜, 묶음 (api_usage_daily 기본 키와 같음)
 */
public record ApiUsageKey(LocalDate usageDate, ApiUsageGroup usageGroup) {
}
