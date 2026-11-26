package kr.suhsaechan.secbrief.web.share;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSharePreview;
import kr.suhsaechan.secbrief.advisory.service.ArticleSharePreviewQueryService;
import kr.suhsaechan.secbrief.web.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공유 미리보기 /a/{id}: 인증 없이 열림, 브리핑 있음(한 줄 결론·무슨 일 + OG 설명 = 한 줄 결론)·없음(OG 설명 = 원문 제목),
 * 대표 이미지 유무별 og:image, 앱 열기·스토어·원문 링크, 브랜드 색 변수, 캐시 헤더, 없는 id·숫자 아닌 id 404 화면 (예시 값)
 */
@WebMvcTest(SharePreviewController.class)
@Import({WebSecurityTestConfig.class, SharePageLinks.class, SharePageBrandColors.class})
class SharePreviewControllerTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-22T16:30:00Z");

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ArticleSharePreviewQueryService articleSharePreviewQueryService;

    @Test
    void rendersBriefingPreviewWithOpenGraphAndAppLinks() throws Exception {
        given(articleSharePreviewQueryService.findSharePreview(1L)).willReturn(Optional.of(new ArticleSharePreview(1L,
                "예시 VPN 인증 우회 취약점", "Example VPN authentication bypass", "예시 VPN 에서 인증 없이 관리자 화면에 들어갈 수 있어요.",
                "예시 VPN 에 인증을 건너뛰는 취약점이 공개됐어요.", "Example News", PUBLISHED_AT,
                "https://example.test/articles/1", false)));

        mockMvc.perform(get("/a/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "max-age=300, public"))
                .andExpect(content().string(containsString("<h1>예시 VPN 인증 우회 취약점</h1>")))
                .andExpect(content().string(containsString("Example VPN authentication bypass")))
                .andExpect(content().string(containsString("예시 VPN 에 인증을 건너뛰는 취약점이 공개됐어요.")))
                // 게시일은 한국 시간 (UTC 11-22 16:30 > 11-23)
                .andExpect(content().string(containsString("2026년 11월 23일")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"예시 VPN 에서 인증 없이 관리자 화면에 들어갈 수 있어요.\"/>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:url\" content=\"https://api.secbrief.suhsaechan.kr/a/1\"/>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:image\" content=\"https://api.secbrief.suhsaechan.kr/share/app-icon.png\"/>")))
                .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary\"/>")))
                .andExpect(content().string(containsString("app-id=6837211510, app-argument=https://api.secbrief.suhsaechan.kr/a/1")))
                .andExpect(content().string(containsString("href=\"secbrief://articles/1\"")))
                .andExpect(content().string(containsString("intent://articles/1#Intent;scheme=secbrief;package=kr.suhsaechan.secbrief;")))
                .andExpect(content().string(containsString("https://apps.apple.com/kr/app/id6837211510")))
                .andExpect(content().string(containsString("https://play.google.com/store/apps/details?id=kr.suhsaechan.secbrief")))
                .andExpect(content().string(containsString("href=\"https://example.test/articles/1\"")))
                .andExpect(content().string(containsString("Example News 원문 보기")))
                // 색은 brand/brand-colors.json 에서 (밝은 바탕 + 어두운 바탕)
                .andExpect(content().string(containsString("--sb-background:#F4F4F0;")))
                .andExpect(content().string(containsString("--sb-surface-raised:")))
                .andExpect(content().string(containsString("@media (prefers-color-scheme: dark){:root{--sb-accent:")));
    }

    @Test
    void rendersPreviewWithoutBriefingUsingOriginalTitleAndArticleImage() throws Exception {
        given(articleSharePreviewQueryService.findSharePreview(2L)).willReturn(Optional.of(new ArticleSharePreview(2L,
                "예시 라우터 원격 실행 취약점", "Example router RCE", null, null, "Example News", PUBLISHED_AT,
                "https://example.test/articles/2", true)));

        mockMvc.perform(get("/a/2"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("한 줄 결론"))))
                .andExpect(content().string(not(containsString("AI 가 요약했어요"))))
                .andExpect(content().string(containsString("<meta property=\"og:description\" content=\"Example router RCE\"/>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:image\" content=\"https://api.secbrief.suhsaechan.kr/api/v1/articles/2/image\"/>")))
                .andExpect(content().string(containsString("<meta name=\"twitter:card\" content=\"summary_large_image\"/>")))
                .andExpect(content().string(containsString("src=\"/api/v1/articles/2/image\"")));
    }

    @Test
    void rendersNotFoundPageForUnknownArticle() throws Exception {
        given(articleSharePreviewQueryService.findSharePreview(404L)).willReturn(Optional.empty());

        mockMvc.perform(get("/a/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string("Cache-Control", "max-age=60, public"))
                .andExpect(content().string(containsString("소식을 찾지 못했어요")))
                .andExpect(content().string(containsString("RESOURCE_NOT_FOUND")))
                .andExpect(content().string(containsString("href=\"secbrief://\"")))
                .andExpect(content().string(containsString("--sb-background:")));
    }

    @Test
    void rendersNotFoundPageForNonNumericIdWithoutQuery() throws Exception {
        mockMvc.perform(get("/a/abc"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("소식을 찾지 못했어요")));
        verify(articleSharePreviewQueryService, never()).findSharePreview(anyLong());
    }
}
