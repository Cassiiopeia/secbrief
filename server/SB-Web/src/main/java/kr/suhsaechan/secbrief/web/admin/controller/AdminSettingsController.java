package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.config.ConfigGroup;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.ConfigValueType;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.config.SystemConfigView;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.admin.support.AdminFlash;
import kr.suhsaechan.secbrief.web.admin.support.AdminSettingGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 설정 화면: 묶음별 설정 목록, 값 바꾸기(사유 선택), 기본값 복원, 키별 변경 이력 (바뀐 내용은 감사 기록)
 * 비밀값은 가린 값만 내려보내고, 전용 화면(AI 키)에서 다루는 설정은 여기서 바꾸지 않음
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminSettingsController {

    private static final String SETTINGS_PATH = "/admin/settings";
    private static final String SETTINGS_VIEW = "admin/settings";
    private static final String SETTINGS_HISTORY_VIEW = "admin/settings-history";
    private static final String REDIRECT_TO_SETTINGS = "redirect:" + SETTINGS_PATH;
    // 꺼진 토글은 값이 오지 않음
    private static final String UNCHECKED_TOGGLE_VALUE = "false";
    // 감사 기록에 값 대신 남기는 글 (비밀값)
    private static final String SECRET_CHANGE_DETAIL = "비밀값 변경";

    private final SystemConfigService systemConfigService;
    private final AdminAuditService adminAuditService;

    @GetMapping(SETTINGS_PATH)
    public String showSettings(Model model) {
        List<SystemConfigView> configViews = systemConfigService.findViews();
        model.addAttribute("settingGroups", Arrays.stream(ConfigGroup.values())
                .map(group -> new AdminSettingGroup(group, configViews.stream()
                        .filter(configView -> configView.key().getGroup() == group)
                        .toList()))
                .toList());
        model.addAttribute("canStoreSecret", systemConfigService.canStoreSecret());
        return SETTINGS_VIEW;
    }

    @GetMapping(SETTINGS_PATH + "/{configKeyName}/history")
    public String showHistory(@PathVariable String configKeyName, Model model, RedirectAttributes redirectAttributes) {
        try {
            ConfigKey key = toConfigKey(configKeyName);
            model.addAttribute("configKey", key);
            model.addAttribute("histories", systemConfigService.findHistories(key));
            return SETTINGS_HISTORY_VIEW;
        } catch (CustomException exception) {
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
            return REDIRECT_TO_SETTINGS;
        }
    }

    /**
     * 값 저장: 키 확인 > 검증·저장·이력(SystemConfigService) > 바뀌었으면 감사 기록 > 목록으로 돌아가 결과 안내
     */
    @PostMapping(SETTINGS_PATH + "/{configKeyName}")
    public String updateSetting(@PathVariable String configKeyName,
                                @RequestParam(name = "value", required = false) String rawValue,
                                @RequestParam(required = false) String reason,
                                Principal principal, RedirectAttributes redirectAttributes) {
        try {
            ConfigKey key = toEditableConfigKey(configKeyName);
            String submittedValue = key.getValueType() == ConfigValueType.BOOLEAN && rawValue == null
                    ? UNCHECKED_TOGGLE_VALUE
                    : rawValue;
            String previousValue = systemConfigService.getString(key);
            boolean isChanged = systemConfigService.update(key, submittedValue, principal.getName(), reason);
            if (!isChanged) {
                AdminFlash.addMessage(redirectAttributes, key.getLabel() + " 설정이 그대로예요");
                return REDIRECT_TO_SETTINGS;
            }
            adminAuditService.record(principal.getName(), AdminAuditAction.CONFIG_UPDATE, key.name(),
                    key.isSecret() ? SECRET_CHANGE_DETAIL : previousValue + " -> " + systemConfigService.getString(key));
            AdminFlash.addMessage(redirectAttributes, key.getLabel() + " 설정을 저장했어요");
        } catch (CustomException exception) {
            logRejection(exception, configKeyName);
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_SETTINGS;
    }

    @PostMapping(SETTINGS_PATH + "/{configKeyName}/reset")
    public String resetSetting(@PathVariable String configKeyName, @RequestParam(required = false) String reason,
                               Principal principal, RedirectAttributes redirectAttributes) {
        try {
            ConfigKey key = toEditableConfigKey(configKeyName);
            String previousValue = systemConfigService.getString(key);
            boolean isReset = systemConfigService.resetToDefault(key, principal.getName(), reason);
            if (!isReset) {
                AdminFlash.addMessage(redirectAttributes, key.getLabel() + " 설정은 이미 기본값이에요");
                return REDIRECT_TO_SETTINGS;
            }
            adminAuditService.record(principal.getName(), AdminAuditAction.CONFIG_RESET, key.name(),
                    key.isSecret() ? SECRET_CHANGE_DETAIL : previousValue + " -> " + systemConfigService.getString(key));
            AdminFlash.addMessage(redirectAttributes, key.getLabel() + " 설정을 기본값으로 돌렸어요");
        } catch (CustomException exception) {
            logRejection(exception, configKeyName);
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_SETTINGS;
    }

    // 전용 화면에서 다루는 설정은 여기서 바꾸지 않음 (검증 규칙이 그 화면에 있음)
    private static ConfigKey toEditableConfigKey(String configKeyName) {
        ConfigKey key = toConfigKey(configKeyName);
        if (key.isManagedOnDedicatedScreen()) {
            throw new CustomException(ErrorCode.CONFIG_NOT_EDITABLE_HERE);
        }
        return key;
    }

    private static ConfigKey toConfigKey(String configKeyName) {
        return Arrays.stream(ConfigKey.values())
                .filter(key -> key.name().equals(configKeyName))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.CONFIG_KEY_NOT_FOUND));
    }

    // 로그용 값 정리 (영문 대문자·숫자·_ 외 > ?, 100자 제한): 주소로 들어온 값이라 그대로 남기지 않음
    private static void logRejection(CustomException exception, String configKeyName) {
        String sanitizedKeyName = configKeyName.replaceAll("[^A-Z0-9_]", "?");
        log.warn("[admin settings] rejected {} : code={}, key={}", exception.getErrorCode().getStatus().value(),
                exception.getErrorCode().name(),
                sanitizedKeyName.length() > 100 ? sanitizedKeyName.substring(0, 100) : sanitizedKeyName);
    }
}
