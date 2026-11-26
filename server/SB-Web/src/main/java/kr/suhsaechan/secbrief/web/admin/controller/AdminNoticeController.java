package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import kr.suhsaechan.secbrief.application.notification.notice.AdminNoticeRow;
import kr.suhsaechan.secbrief.application.notification.notice.AdminNoticeService;
import kr.suhsaechan.secbrief.application.notification.notice.NoticeForm;
import kr.suhsaechan.secbrief.application.notification.notice.NoticePublishResult;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
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
 * 공지사항 관리: 표 + 새 공지 쓰기(선택: 푸시도 보내기), 고치기, 고정·풀기, 삭제 (바뀐 내용은 감사 기록)
 * 입력이 틀리면 쓴 내용을 그대로 둔 채 같은 화면에 안내 (리다이렉트하지 않음)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminNoticeController {

    private static final String NOTICES_PATH = "/admin/notices";
    private static final String NOTICES_VIEW = "admin/notices";
    private static final String NOTICE_EDIT_VIEW = "admin/notice-edit";
    private static final String REDIRECT_TO_NOTICES = "redirect:" + NOTICES_PATH;
    private static final NoticeForm EMPTY_NOTICE_FORM = new NoticeForm("", "", "", false);

    private final AdminNoticeService adminNoticeService;
    private final AdminAuditService adminAuditService;

    @GetMapping(NOTICES_PATH)
    public String showNotices(Model model) {
        return renderNotices(model, EMPTY_NOTICE_FORM, false);
    }

    /**
     * 올리기: 저장 > (선택) 푸시 > 감사 기록 > 표로 돌아가 결과 안내
     * pinned·sendPush: 체크 상자 (끄면 값이 오지 않음)
     */
    @PostMapping(NOTICES_PATH)
    public String publishNotice(@RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String body,
                                @RequestParam(defaultValue = "") String linkUrl,
                                @RequestParam(name = "pinned", defaultValue = "false") boolean isPinned,
                                @RequestParam(name = "sendPush", defaultValue = "false") boolean shouldSendPush,
                                Principal principal, Model model, RedirectAttributes redirectAttributes) {
        NoticeForm noticeForm = new NoticeForm(title, body, linkUrl, isPinned);
        try {
            NoticePublishResult publishResult = adminNoticeService.publish(noticeForm, shouldSendPush);
            adminAuditService.record(principal.getName(), AdminAuditAction.NOTICE_CREATE,
                    String.valueOf(publishResult.noticeId()), "sendPush " + shouldSendPush + ", sent "
                            + publishResult.sentDeviceCount() + ", held " + publishResult.heldDeviceCount());
            AdminFlash.addMessage(redirectAttributes, toPublishMessage(publishResult, shouldSendPush));
            return REDIRECT_TO_NOTICES;
        } catch (CustomException exception) {
            logRejected("publish", exception, null);
            model.addAttribute(AdminFlash.ERROR_ATTRIBUTE, AdminFlash.toErrorText(exception.getErrorCode()));
            return renderNotices(model, noticeForm, shouldSendPush);
        }
    }

    @GetMapping(NOTICES_PATH + "/{noticeId}/edit")
    public String showNoticeEdit(@PathVariable long noticeId, Model model, RedirectAttributes redirectAttributes) {
        try {
            AdminNoticeRow notice = adminNoticeService.findNotice(noticeId);
            return renderEdit(model, noticeId, new NoticeForm(notice.title(), notice.body(),
                    notice.linkUrl() == null ? "" : notice.linkUrl(), notice.isPinned()));
        } catch (CustomException exception) {
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
            return REDIRECT_TO_NOTICES;
        }
    }

    @PostMapping(NOTICES_PATH + "/{noticeId}")
    public String updateNotice(@PathVariable long noticeId, @RequestParam(defaultValue = "") String title,
                               @RequestParam(defaultValue = "") String body, @RequestParam(defaultValue = "") String linkUrl,
                               @RequestParam(name = "pinned", defaultValue = "false") boolean isPinned,
                               Principal principal, Model model, RedirectAttributes redirectAttributes) {
        NoticeForm noticeForm = new NoticeForm(title, body, linkUrl, isPinned);
        try {
            adminNoticeService.update(noticeId, noticeForm);
            adminAuditService.record(principal.getName(), AdminAuditAction.NOTICE_UPDATE, String.valueOf(noticeId),
                    "pinned " + isPinned);
            AdminFlash.addMessage(redirectAttributes, "공지사항을 고쳤어요");
            return REDIRECT_TO_NOTICES;
        } catch (CustomException exception) {
            logRejected("update", exception, noticeId);
            model.addAttribute(AdminFlash.ERROR_ATTRIBUTE, AdminFlash.toErrorText(exception.getErrorCode()));
            return renderEdit(model, noticeId, noticeForm);
        }
    }

    @PostMapping(NOTICES_PATH + "/{noticeId}/pin")
    public String changeNoticePinned(@PathVariable long noticeId,
                                     @RequestParam(name = "pinned", defaultValue = "false") boolean isPinned,
                                     Principal principal, RedirectAttributes redirectAttributes) {
        try {
            adminNoticeService.changePinned(noticeId, isPinned);
            adminAuditService.record(principal.getName(), AdminAuditAction.NOTICE_PIN, String.valueOf(noticeId),
                    "pinned " + isPinned);
            AdminFlash.addMessage(redirectAttributes, isPinned ? "공지사항을 맨 위에 고정했어요" : "고정을 풀었어요");
        } catch (CustomException exception) {
            logRejected("pin", exception, noticeId);
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_NOTICES;
    }

    @PostMapping(NOTICES_PATH + "/{noticeId}/delete")
    public String deleteNotice(@PathVariable long noticeId, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            AdminNoticeRow deletedNotice = adminNoticeService.delete(noticeId);
            adminAuditService.record(principal.getName(), AdminAuditAction.NOTICE_DELETE, String.valueOf(noticeId),
                    "title length " + deletedNotice.title().length());
            AdminFlash.addMessage(redirectAttributes, "공지사항을 삭제했어요");
        } catch (CustomException exception) {
            logRejected("delete", exception, noticeId);
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_NOTICES;
    }

    private String renderNotices(Model model, NoticeForm noticeForm, boolean shouldSendPush) {
        model.addAttribute("noticeRows", adminNoticeService.findAllNotices());
        model.addAttribute("noticeForm", noticeForm);
        model.addAttribute("shouldSendPush", shouldSendPush);
        model.addAttribute("pushRecipientCount", adminNoticeService.countPushRecipients());
        model.addAttribute("maximumTitleLength", NoticeForm.MAXIMUM_TITLE_LENGTH);
        model.addAttribute("maximumBodyLength", NoticeForm.MAXIMUM_BODY_LENGTH);
        return NOTICES_VIEW;
    }

    private String renderEdit(Model model, long noticeId, NoticeForm noticeForm) {
        model.addAttribute("noticeId", noticeId);
        model.addAttribute("noticeForm", noticeForm);
        model.addAttribute("maximumTitleLength", NoticeForm.MAXIMUM_TITLE_LENGTH);
        model.addAttribute("maximumBodyLength", NoticeForm.MAXIMUM_BODY_LENGTH);
        return NOTICE_EDIT_VIEW;
    }

    // 결과 안내: 푸시 안 보냄 / 보낸 기기 수 + 보류 기기 수
    private static String toPublishMessage(NoticePublishResult publishResult, boolean shouldSendPush) {
        if (!shouldSendPush) {
            return "공지사항을 올렸어요";
        }
        if (publishResult.sentDeviceCount() + publishResult.heldDeviceCount() == 0) {
            return "공지사항을 올렸어요 (알림을 받은 기기는 없어요)";
        }
        String message = "공지사항을 올리고 기기 " + publishResult.sentDeviceCount() + "대에 알림을 보냈어요";
        if (publishResult.heldDeviceCount() > 0) {
            message += " (방해 금지 시간인 " + publishResult.heldDeviceCount() + "대는 끝나면 보내요)";
        }
        return message;
    }

    private static void logRejected(String operation, CustomException exception, Long noticeId) {
        log.warn("[admin notice] rejected {} : operation={}, code={}, noticeId={}",
                exception.getErrorCode().getStatus().value(), operation, exception.getErrorCode().name(), noticeId);
    }
}
