package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.push.PushMessage;
import kr.suhsaechan.secbrief.member.push.PushSendOutcome;
import kr.suhsaechan.secbrief.member.push.PushSender;
import kr.suhsaechan.secbrief.member.repository.HeldPushRepository;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.member.repository.PushTicketRepository;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 공지사항 관리 (DB): 올리기(푸시 없이 / 푸시도: 깨어 있는 기기는 발송, 방해 금지 기기는 보류), 입력 검증(쓴 내용 유지),
 * 고치기·고정·삭제 + 감사 기록, 공지·알림 기록 화면 (발송기는 가짜)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminNoticeManagementTest extends PostgresContainerTestSupport {

    private static final String NOTICES_PATH = "/admin/notices";
    private static final String AWAKE_TOKEN = "ExponentPushToken[notice-awake]";
    private static final String QUIET_TOKEN = "ExponentPushToken[notice-quiet]";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private HeldPushRepository heldPushRepository;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private PushTicketRepository pushTicketRepository;
    @Autowired
    private PushDeviceService pushDeviceService;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @MockitoBean
    private PushSender pushSender;

    private final String adminUsername = AdminTestAccounts.randomUsername();

    @BeforeEach
    void clearTablesAndEnableSender() {
        pushTicketRepository.deleteAll();
        pushDeviceRepository.deleteAll();
        notificationRepository.deleteAll();
        given(pushSender.isAvailable()).willReturn(true);
        given(pushSender.send(anyList())).willAnswer(invocation -> {
            List<PushMessage> pushMessages = invocation.getArgument(0);
            return pushMessages.stream()
                    .map(pushMessage -> PushSendOutcome.accepted(pushMessage.pushToken(), "ticket-" + pushMessage.pushToken()))
                    .toList();
        });
    }

    @Test
    void publishesNoticeWithoutPushKeepingLineBreaks() throws Exception {
        // 브라우저 textarea 는 CRLF 로 보냄: LF 로 저장 (#87)
        postNotice("서비스 점검 안내", "11월 30일 새벽 2시에 점검해요.\r\n10분이면 끝나요.", "", true, false)
                .andExpect(redirectedUrl(NOTICES_PATH))
                .andExpect(flash().attribute("flashMessage", "공지사항을 올렸어요"));

        Notification notice = notificationRepository.findAll().get(0);
        assertThat(notice.getKind()).isEqualTo(NotificationKind.NOTICE);
        assertThat(notice.getBody()).isEqualTo("11월 30일 새벽 2시에 점검해요.\n10분이면 끝나요.");
        assertThat(notice.isPinned()).isTrue();
        assertThat(notice.getLinkUrl()).isNull();
        assertThat(notice.getPushedAt()).isNull();
        verify(pushSender, never()).send(anyList());
        assertThat(latestAuditAction()).isEqualTo(AdminAuditAction.NOTICE_CREATE);
    }

    @Test
    void sendsNoticePushToAwakeDevicesAndHoldsForQuietDevices() throws Exception {
        LocalTime nowTime = LocalTime.now(ZoneOffset.UTC);
        register(AWAKE_TOKEN, new QuietHoursRequest(false, LocalTime.of(22, 0), LocalTime.of(8, 0), "UTC"));
        register(QUIET_TOKEN, new QuietHoursRequest(true, nowTime.minusHours(1), nowTime.plusHours(1), "UTC"));

        postNotice("새 기능: 알림함", "받은 알림을 알림함에서 다시 볼 수 있어요.", "https://secbrief.suhsaechan.kr", false, true)
                .andExpect(flash().attribute("flashMessage", containsString("기기 1대에 알림을 보냈어요")))
                .andExpect(flash().attribute("flashMessage", containsString("방해 금지 시간인 1대는 끝나면 보내요")));

        ArgumentCaptor<List<PushMessage>> sentMessages = ArgumentCaptor.captor();
        verify(pushSender).send(sentMessages.capture());
        Notification notice = notificationRepository.findAll().get(0);
        assertThat(sentMessages.getValue()).singleElement().satisfies(pushMessage -> {
            assertThat(pushMessage.pushToken()).isEqualTo(AWAKE_TOKEN);
            assertThat(pushMessage.title()).isEqualTo("새 기능: 알림함");
            assertThat(pushMessage.data()).containsEntry("notificationKind", "NOTICE")
                    .containsEntry("notificationId", String.valueOf(notice.getId()));
        });
        assertThat(notice.getPushedAt()).isNotNull();
        assertThat(heldPushRepository.findAll()).singleElement()
                .satisfies(heldPush -> assertThat(heldPush.getNotificationId()).isEqualTo(notice.getId()));
    }

    @Test
    void rejectsInvalidInputAndKeepsWrittenText() throws Exception {
        postNotice("", "본문", "", false, false)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOTICE_TITLE_LENGTH")));
        postNotice("링크 안내", "본문은 그대로 남아요", "javascript:alert(1)", false, false)
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOTICE_LINK_URL_INVALID")))
                .andExpect(content().string(containsString("본문은 그대로 남아요")));
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void updatesPinsAndDeletesNoticeWithAuditLogs() throws Exception {
        postNotice("점검 안내", "본문", "", false, false);
        Long noticeId = notificationRepository.findAll().get(0).getId();

        mockMvc.perform(post(NOTICES_PATH + "/" + noticeId).with(user(adminUsername).roles("ADMIN")).with(csrf())
                        .param("title", "점검 안내 (시간 변경)").param("body", "새 본문").param("linkUrl", "https://example.com/notice"))
                .andExpect(redirectedUrl(NOTICES_PATH));
        Notification updatedNotice = notificationRepository.findById(noticeId).orElseThrow();
        assertThat(updatedNotice.getTitle()).isEqualTo("점검 안내 (시간 변경)");
        assertThat(updatedNotice.getLinkUrl()).isEqualTo("https://example.com/notice");
        assertThat(latestAuditAction()).isEqualTo(AdminAuditAction.NOTICE_UPDATE);

        mockMvc.perform(post(NOTICES_PATH + "/" + noticeId + "/pin").with(user(adminUsername).roles("ADMIN")).with(csrf())
                        .param("pinned", "true"))
                .andExpect(flash().attribute("flashMessage", "공지사항을 맨 위에 고정했어요"));
        assertThat(notificationRepository.findById(noticeId).orElseThrow().isPinned()).isTrue();

        mockMvc.perform(post(NOTICES_PATH + "/" + noticeId + "/delete").with(user(adminUsername).roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("flashMessage", "공지사항을 삭제했어요"));
        assertThat(notificationRepository.existsById(noticeId)).isFalse();
        assertThat(latestAuditAction()).isEqualTo(AdminAuditAction.NOTICE_DELETE);

        mockMvc.perform(post(NOTICES_PATH + "/" + noticeId + "/delete").with(user(adminUsername).roles("ADMIN")).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("RESOURCE_NOT_FOUND")));
    }

    @Test
    void rendersNoticeAndAlertHistoryPages() throws Exception {
        register(AWAKE_TOKEN, null);
        postNotice("이용 안내", "본문", "", true, false);
        Long noticeId = notificationRepository.findAll().get(0).getId();

        getAsAdmin(NOTICES_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("새 공지 쓰기")))
                .andExpect(content().string(containsString("알림을 켠 기기 1대가 받아요")))
                .andExpect(content().string(containsString("이용 안내")))
                .andExpect(content().string(containsString("href=\"/admin/alerts\"")));
        getAsAdmin(NOTICES_PATH + "/" + noticeId + "/edit").andExpect(status().isOk())
                .andExpect(content().string(containsString("공지사항 고치기")));
        getAsAdmin("/admin/alerts").andExpect(status().isOk())
                .andExpect(content().string(containsString("최근 30일 동안 보낸 긴급 알림이 없어요")));
        // 기록 행 (종류 배지·본문)
        notificationRepository.save(Notification.createAlert(AlertCategory.KEV, "악용 중인 취약점이 확인됐어요",
                "CVE-2026-0001: 예시 인증 우회", List.of(), java.time.Instant.now()));
        getAsAdmin("/admin/alerts").andExpect(status().isOk())
                .andExpect(content().string(containsString("악용 중인 취약점")))
                .andExpect(content().string(containsString("CVE-2026-0001: 예시 인증 우회")));
        getAsAdmin("/admin/alerts?cursor=broken").andExpect(status().isOk())
                .andExpect(content().string(containsString("INVALID_CURSOR")));
    }

    private void register(String pushToken, QuietHoursRequest quietHours) {
        pushDeviceService.register(new PushDeviceRegisterRequest(pushToken, PushPlatform.ANDROID, Set.of(AlertCategory.KEV), quietHours));
    }

    private AdminAuditAction latestAuditAction() {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, 1)).getContent().get(0).getAction();
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(adminUsername).roles("ADMIN")));
    }

    private ResultActions postNotice(String title, String body, String linkUrl, boolean isPinned, boolean shouldSendPush)
            throws Exception {
        var request = post(NOTICES_PATH).with(user(adminUsername).roles("ADMIN")).with(csrf())
                .param("title", title).param("body", body).param("linkUrl", linkUrl);
        if (isPinned) {
            request.param("pinned", "true");
        }
        if (shouldSendPush) {
            request.param("sendPush", "true");
        }
        return mockMvc.perform(request);
    }
}
