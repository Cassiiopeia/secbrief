package kr.suhsaechan.secbrief.web.admin.support;

import java.util.List;
import kr.suhsaechan.secbrief.common.config.ConfigGroup;
import kr.suhsaechan.secbrief.common.config.SystemConfigView;

/**
 * 설정 화면의 카드 1개: 묶음 + 그 묶음의 설정 목록 (ConfigKey 선언 순서)
 */
public record AdminSettingGroup(ConfigGroup group, List<SystemConfigView> configViews) {
}
