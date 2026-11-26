package kr.suhsaechan.secbrief.web.share;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 앱 링크 연결 파일: 인증 없이 200 JSON(이동 없음), iOS App ID·/a/* 경로(새·이전 형식), Android 패키지·서명 지문 3개, 캐시 1시간
 */
@WebMvcTest(AppLinkAssociationController.class)
@Import(WebSecurityTestConfig.class)
class AppLinkAssociationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesAppleAppSiteAssociationAsJsonForArticlePreviewPaths() throws Exception {
        mockMvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(jsonPath("$.applinks.apps", hasSize(0)))
                .andExpect(jsonPath("$.applinks.details", hasSize(1)))
                .andExpect(jsonPath("$.applinks.details[0].appIDs[0]").value("CUK22HY6YC.kr.suhsaechan.secbrief"))
                .andExpect(jsonPath("$.applinks.details[0].components[0]['/']").value("/a/*"))
                .andExpect(jsonPath("$.applinks.details[0].appID").value("CUK22HY6YC.kr.suhsaechan.secbrief"))
                .andExpect(jsonPath("$.applinks.details[0].paths[0]").value("/a/*"));
    }

    @Test
    void servesAndroidAssetLinksWithPlaySigningAndUploadFingerprints() throws Exception {
        mockMvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].relation[0]").value("delegate_permission/common.handle_all_urls"))
                .andExpect(jsonPath("$[0].target.namespace").value("android_app"))
                .andExpect(jsonPath("$[0].target.package_name").value("kr.suhsaechan.secbrief"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints", hasSize(3)))
                // Play Console 디지털 애셋 링크 JSON 값
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]")
                        .value("71:35:25:5D:03:5E:E4:70:0B:58:A8:77:8E:C9:02:0F:BC:29:7D:5A:74:D9:46:EA:D7:D5:F5:1C:91:B0:0A:55"));
    }
}
