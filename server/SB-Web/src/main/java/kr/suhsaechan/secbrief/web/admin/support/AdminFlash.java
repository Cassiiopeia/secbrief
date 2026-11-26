package kr.suhsaechan.secbrief.web.admin.support;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 리다이렉트 뒤 토스트 문구 전달: 성공(flashMessage), 실패(flashError, 문구 + 에러 코드)
 * admin-layout 의 flash 자리가 이 이름을 읽음
 */
public final class AdminFlash {

    public static final String MESSAGE_ATTRIBUTE = "flashMessage";
    public static final String ERROR_ATTRIBUTE = "flashError";

    private AdminFlash() {
    }

    public static void addMessage(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(MESSAGE_ATTRIBUTE, message);
    }

    public static void addError(RedirectAttributes redirectAttributes, ErrorCode errorCode) {
        redirectAttributes.addFlashAttribute(ERROR_ATTRIBUTE, toErrorText(errorCode));
    }

    /**
     * ErrorCode > 화면 문구 (예: 현재 비밀번호가 맞지 않아요. (ADMIN_CURRENT_PASSWORD_MISMATCH))
     */
    public static String toErrorText(ErrorCode errorCode) {
        return errorCode.getMessage() + " (" + errorCode.name() + ")";
    }
}
