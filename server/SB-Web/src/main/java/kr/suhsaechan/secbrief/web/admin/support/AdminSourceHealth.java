package kr.suhsaechan.secbrief.web.admin.support;

import java.util.List;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceRow;

/**
 * 출처 상태 요약 (대시보드·출처 관리 공용): 전체·켜진 출처 수, 켜져 있는데 마지막 수집이 실패한 출처
 * 꺼진 출처의 지난 실패는 세지 않음 (수집하지 않으니 확인할 일이 아님)
 */
public record AdminSourceHealth(int totalSourceCount, int enabledSourceCount, List<AdminSourceRow> failingSourceRows) {

    public static AdminSourceHealth of(List<AdminSourceRow> sourceRows) {
        int enabledSourceCount = (int) sourceRows.stream().filter(AdminSourceRow::isEnabled).count();
        List<AdminSourceRow> failingSourceRows = sourceRows.stream()
                .filter(sourceRow -> sourceRow.isEnabled() && sourceRow.isLastCollectFailed())
                .toList();
        return new AdminSourceHealth(sourceRows.size(), enabledSourceCount, failingSourceRows);
    }

    public int failingSourceCount() {
        return failingSourceRows.size();
    }

    public boolean hasFailingSource() {
        return !failingSourceRows.isEmpty();
    }
}
