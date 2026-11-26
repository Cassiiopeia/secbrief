package kr.suhsaechan.secbrief.application.admin.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 출처 설정 변경 결과: 바뀐 항목만 감사 기록 상세에 넣음
 */
class SourceSettingChangeTest {

    @Test
    void describesOnlyChangedFields() {
        assertThat(new SourceSettingChange("KISA", "KISA 보호나라", true, false, 30, 30).toAuditDetail())
                .isEqualTo("enabled true -> false");
        assertThat(new SourceSettingChange("KISA", "KISA 보호나라", true, true, 30, 60).toAuditDetail())
                .isEqualTo("intervalMinutes 30 -> 60");
        assertThat(new SourceSettingChange("KISA", "KISA 보호나라", false, true, 30, 60).toAuditDetail())
                .isEqualTo("enabled false -> true, intervalMinutes 30 -> 60");
    }

    @Test
    void reportsUnchangedWhenValuesAreSame() {
        assertThat(new SourceSettingChange("KISA", "KISA 보호나라", true, true, 30, 30).isChanged()).isFalse();
        assertThat(new SourceSettingChange("KISA", "KISA 보호나라", true, false, 30, 30).isChanged()).isTrue();
    }
}
