package kr.suhsaechan.secbrief.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.dto.QuietHoursResponse;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림 기기 API: {data} 응답, 토큰 형식·필수값 검증(VALIDATION_ERROR), 모르는 종류(MESSAGE_NOT_READABLE),
 * 방해 금지 시간 (없으면 null 로 넘겨 저장값 유지, 시각 모양·필수값 검증)
 */
@WebMvcTest(PushDeviceController.class)
@Import(WebSecurityTestConfig.class)
class PushDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PushDeviceService pushDeviceService;

    @Test
    void registersDeviceAndReturnsEnabledCategories() throws Exception {
        given(pushDeviceService.register(any())).willReturn(new PushDeviceResponse(List.of(AlertCategory.KEV)));

        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc-123_x]","platform":"ANDROID","enabledAlertCategories":["KEV"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledAlertCategories[0]").value("KEV"));
    }

    @Test
    void acceptsEmptyCategoriesAsTurnOff() throws Exception {
        given(pushDeviceService.register(any())).willReturn(new PushDeviceResponse(List.of()));

        putPushDevice("""
                {"pushToken":"ExpoPushToken[abc]","platform":"IOS","enabledAlertCategories":[]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledAlertCategories").isEmpty());
    }

    @Test
    void acceptsQuietHoursAndReturnsSavedValue() throws Exception {
        given(pushDeviceService.register(any())).willReturn(new PushDeviceResponse(List.of(AlertCategory.KEV),
                new QuietHoursResponse(true, LocalTime.of(23, 30), LocalTime.of(7, 0), "Asia/Seoul")));

        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]","platform":"IOS","enabledAlertCategories":["KEV"],
                 "quietHours":{"isEnabled":true,"startTime":"23:30","endTime":"07:00","timeZone":"Asia/Seoul"}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quietHours.isEnabled").value(true))
                .andExpect(jsonPath("$.data.quietHours.startTime").value("23:30"))
                .andExpect(jsonPath("$.data.quietHours.endTime").value("07:00"));
        ArgumentCaptor<PushDeviceRegisterRequest> request = ArgumentCaptor.forClass(PushDeviceRegisterRequest.class);
        verify(pushDeviceService).register(request.capture());
        assertThat(request.getValue().quietHours().startTime()).isEqualTo(LocalTime.of(23, 30));
    }

    @Test
    void passesNullQuietHoursForOlderAppAndReturnsDefault() throws Exception {
        given(pushDeviceService.register(any())).willReturn(new PushDeviceResponse(List.of(AlertCategory.KEV)));

        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]","platform":"ANDROID","enabledAlertCategories":["KEV"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quietHours.isEnabled").value(false))
                .andExpect(jsonPath("$.data.quietHours.startTime").value("22:00"));
        ArgumentCaptor<PushDeviceRegisterRequest> request = ArgumentCaptor.forClass(PushDeviceRegisterRequest.class);
        verify(pushDeviceService).register(request.capture());
        assertThat(request.getValue().quietHours()).isNull();
    }

    @Test
    void rejectsMalformedQuietHours() throws Exception {
        // 시각 모양이 틀림 / 필수값 빠짐
        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]","platform":"IOS","enabledAlertCategories":["KEV"],
                 "quietHours":{"isEnabled":true,"startTime":"25:00","endTime":"07:00","timeZone":"Asia/Seoul"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MESSAGE_NOT_READABLE"));
        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]","platform":"IOS","enabledAlertCategories":["KEV"],
                 "quietHours":{"isEnabled":true,"startTime":"22:00","endTime":"07:00"}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(pushDeviceService, never()).register(any());
    }

    @Test
    void rejectsMalformedPushToken() throws Exception {
        putPushDevice("""
                {"pushToken":"not-a-token","platform":"ANDROID","enabledAlertCategories":["KEV"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        verify(pushDeviceService, never()).register(any());
    }

    @Test
    void rejectsMissingPlatformAndCategories() throws Exception {
        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUnknownAlertCategory() throws Exception {
        putPushDevice("""
                {"pushToken":"ExponentPushToken[abc]","platform":"ANDROID","enabledAlertCategories":["WEATHER"]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MESSAGE_NOT_READABLE"));
    }

    private org.springframework.test.web.servlet.ResultActions putPushDevice(String requestJson) throws Exception {
        return mockMvc.perform(put("/api/v1/push-devices").contentType(MediaType.APPLICATION_JSON).content(requestJson));
    }
}
