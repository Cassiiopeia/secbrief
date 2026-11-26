package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import kr.suhsaechan.secbrief.member.admin.AdminPasswordPolicy;
import kr.suhsaechan.secbrief.web.admin.support.AdminFlash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 비밀번호 변경: 현재 비밀번호 확인 + 새 비밀번호 + 확인 값 (바꾸면 감사 기록, 비밀번호 값은 남기지 않음)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminPasswordController {

    private static final String PASSWORD_PATH = "/admin/password";
    private static final String PASSWORD_VIEW = "admin/password";
    private static final String REDIRECT_TO_PASSWORD = "redirect:" + PASSWORD_PATH;

    private final AdminAccountService adminAccountService;
    private final AdminAuditService adminAuditService;

    @GetMapping(PASSWORD_PATH)
    public String showPasswordForm(Model model) {
        model.addAttribute("minimumPasswordLength", AdminPasswordPolicy.MINIMUM_LENGTH);
        return PASSWORD_VIEW;
    }

    /**
     * 비밀번호 변경: 확인 값 대조 > 현재 비밀번호·규칙 검사와 저장 > 감사 기록 > 결과 안내
     */
    @PostMapping(PASSWORD_PATH)
    public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String newPasswordConfirm,
                                 Principal principal, RedirectAttributes redirectAttributes) {
        try {
            if (!newPassword.equals(newPasswordConfirm)) {
                throw new CustomException(ErrorCode.ADMIN_PASSWORD_CONFIRM_MISMATCH);
            }
            adminAccountService.changePassword(principal.getName(), currentPassword, newPassword);
            adminAuditService.record(principal.getName(), AdminAuditAction.PASSWORD_CHANGE, principal.getName(), null);
            AdminFlash.addMessage(redirectAttributes, "비밀번호를 바꿨어요");
        } catch (CustomException exception) {
            log.warn("[admin password] rejected {} : code={}, username={}", exception.getErrorCode().getStatus().value(),
                    exception.getErrorCode().name(), principal.getName());
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_PASSWORD;
    }
}
