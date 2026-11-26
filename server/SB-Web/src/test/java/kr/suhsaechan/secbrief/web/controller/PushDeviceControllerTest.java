package kr.suhsaechan.secbrief.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 알림 기기 API: {data} 응답, 토큰 형식·필수값 검증(VALIDATION_ERROR), 모르는 종류(MESSAGE_NOT_READABLE)
 */
@WebMvcTest(PushDeviceController.class)
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
