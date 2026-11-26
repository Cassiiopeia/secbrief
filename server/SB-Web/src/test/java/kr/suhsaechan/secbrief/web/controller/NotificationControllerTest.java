package kr.suhsaechan.secbrief.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationDetailResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationLatestResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationPageResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationQueryService;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationSummaryResponse;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림함 API: {data} 응답 모양(isPinned 이름, ISO 시각), kind 기본 ALERT, 모르는 kind(TYPE_MISMATCH), 최근 번호, 없는 상세(404)
 */
@WebMvcTest(NotificationController.class)
@Import(WebSecurityTestConfig.class)
class NotificationControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-26T15:51:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationQueryService notificationQueryService;

    @Test
    void listsAlertsByDefaultWithSummaryFields() throws Exception {
        given(notificationQueryService.findNotifications(eq(NotificationKind.ALERT), any(), any()))
                .willReturn(new NotificationPageResponse(List.of(new NotificationSummaryResponse(7L, NotificationKind.ALERT,
                        AlertCategory.KEV, List.of(AlertCategory.KEV, AlertCategory.CRITICAL_CVE), "긴급 보안 소식 4건이 들어왔어요",
                        "Ivanti 원격 코드 실행 취약점 외 3건", 501L, "Ivanti 원격 코드 실행 취약점", 4, false, CREATED_AT,
                        CREATED_AT, CREATED_AT)), "next-cursor"));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].kind").value("ALERT"))
                .andExpect(jsonPath("$.data.items[0].alertCategories[1]").value("CRITICAL_CVE"))
                .andExpect(jsonPath("$.data.items[0].leadArticleTitle").value("Ivanti 원격 코드 실행 취약점"))
                .andExpect(jsonPath("$.data.items[0].articleCount").value(4))
                .andExpect(jsonPath("$.data.items[0].isPinned").value(false))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-26T15:51:00Z"))
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"));
    }

    @Test
    void rejectsUnknownKind() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("kind", "WEATHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    @Test
    void returnsLatestIdsWithNullWhenEmpty() throws Exception {
        given(notificationQueryService.findLatestIds()).willReturn(new NotificationLatestResponse(12L, null));

        mockMvc.perform(get("/api/v1/notifications/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestAlertId").value(12))
                .andExpect(jsonPath("$.data.latestNoticeId").isEmpty());
    }

    @Test
    void returnsNoticeDetailAndNotFound() throws Exception {
        given(notificationQueryService.findNotification(999L)).willThrow(new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        given(notificationQueryService.findNotification(3L)).willReturn(new NotificationDetailResponse(3L,
                NotificationKind.NOTICE, null, List.of(), "서비스 점검 안내", "11월 30일 새벽에 점검해요.\n잠시 쉬어 가요.",
                "https://secbrief.suhsaechan.kr", null, null, 0, true, CREATED_AT, CREATED_AT, null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/notifications/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.body").value("11월 30일 새벽에 점검해요.\n잠시 쉬어 가요."))
                .andExpect(jsonPath("$.data.linkUrl").value("https://secbrief.suhsaechan.kr"))
                .andExpect(jsonPath("$.data.isPinned").value(true))
                .andExpect(jsonPath("$.data.pushedAt").isEmpty())
                .andExpect(jsonPath("$.data.articleSections").isEmpty())
                .andExpect(jsonPath("$.data.articleGuides").isEmpty());
        mockMvc.perform(get("/api/v1/notifications/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
