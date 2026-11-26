package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceService;
import kr.suhsaechan.secbrief.application.admin.source.SourceSettingChange;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.admin.support.AdminFlash;
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
 * 출처 관리: 전체 출처 표, 켜기·끄기와 수집 주기 변경 (바뀐 내용은 감사 기록)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminSourceController {

    private static final String SOURCES_PATH = "/admin/sources";
    private static final String SOURCES_VIEW = "admin/sources";
    private static final String REDIRECT_TO_SOURCES = "redirect:" + SOURCES_PATH;

    private final AdminSourceService adminSourceService;
    private final AdminAuditService adminAuditService;

    @GetMapping(SOURCES_PATH)
    public String showSources(Model model) {
        model.addAttribute("sourceRows", adminSourceService.findAllSources());
        return SOURCES_VIEW;
    }

    /**
     * 수집 설정 저장: 주기 숫자 확인 > 변경 > 바뀌었으면 감사 기록 > 표로 돌아가 결과 안내
     * enabled: 켜짐이면 true (꺼진 체크 상자는 값이 오지 않음)
     */
    @PostMapping(SOURCES_PATH + "/{sourceId}")
    public String updateSource(@PathVariable long sourceId,
                               @RequestParam(name = "enabled", defaultValue = "false") boolean isEnabled,
                               @RequestParam(required = false) String intervalMinutes,
                               Principal principal, RedirectAttributes redirectAttributes) {
        try {
            SourceSettingChange change = adminSourceService.updateCollectSettings(sourceId, isEnabled,
                    parseIntervalMinutes(intervalMinutes));
            if (!change.isChanged()) {
                AdminFlash.addMessage(redirectAttributes, change.displayName() + " 설정이 그대로예요");
                return REDIRECT_TO_SOURCES;
            }
            adminAuditService.record(principal.getName(), AdminAuditAction.SOURCE_UPDATE, change.sourceCode(),
                    change.toAuditDetail());
            log.info("[admin source] updated : source={}, {}", change.sourceCode(), change.toAuditDetail());
            AdminFlash.addMessage(redirectAttributes, change.displayName() + " 설정을 저장했어요");
        } catch (CustomException exception) {
            log.warn("[admin source] rejected {} : code={}, sourceId={}", exception.getErrorCode().getStatus().value(),
                    exception.getErrorCode().name(), sourceId);
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_SOURCES;
    }

    // 숫자가 아니면 범위 밖과 같은 안내
    private static int parseIntervalMinutes(String intervalMinutes) {
        try {
            return Integer.parseInt(intervalMinutes == null ? "" : intervalMinutes.trim());
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.SOURCE_INTERVAL_OUT_OF_RANGE);
        }
    }
}
