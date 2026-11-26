package kr.suhsaechan.secbrief.web.share;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSharePreview;
import kr.suhsaechan.secbrief.advisory.service.ArticleSharePreviewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 공유 링크 미리보기 (인증 없음): /a/{articleId} > 기사 미리보기 화면, 없는·잘못된 id 는 같은 모양의 404 화면
 * 앱이 설치돼 있으면 유니버설 링크·앱 링크로 앱이 먼저 열리고, 이 화면은 앱이 없을 때·링크 미리보기(OG)용
 */
@Controller
@RequiredArgsConstructor
public class SharePreviewController {

    static final String ARTICLE_PREVIEW_VIEW = "share/article-preview";
    static final String ARTICLE_NOT_FOUND_VIEW = "share/article-not-found";
    // 숫자 id 만 조회 (그 밖은 DB 를 거치지 않고 404, Long 범위 안 18자리까지)
    private static final Pattern ARTICLE_ID_PATTERN = Pattern.compile("^[1-9][0-9]{0,17}$");
    // 요약이 늦게 붙을 수 있어 짧게 (5분), 없는 기사는 곧 생길 수 있어 더 짧게 (1분)
    private static final CacheControl ARTICLE_PREVIEW_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();
    private static final CacheControl ARTICLE_NOT_FOUND_CACHE = CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic();

    private final ArticleSharePreviewQueryService articleSharePreviewQueryService;
    private final SharePageLinks sharePageLinks;
    private final ShareLinkProperties shareLinkProperties;
    private final SharePageBrandColors sharePageBrandColors;

    @GetMapping(SharePaths.ARTICLE_PREVIEW)
    public String showArticlePreview(@PathVariable String articleId, Model model, HttpServletResponse response) {
        model.addAttribute("brandCssVariables", sharePageBrandColors.getCssVariables());
        model.addAttribute("links", sharePageLinks);
        Optional<ArticleSharePreview> sharePreview = findSharePreview(articleId);
        if (sharePreview.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setHeader(HttpHeaders.CACHE_CONTROL, ARTICLE_NOT_FOUND_CACHE.getHeaderValue());
            return ARTICLE_NOT_FOUND_VIEW;
        }
        model.addAttribute("page", ArticleSharePageView.of(sharePreview.get(), sharePageLinks, shareLinkProperties));
        response.setHeader(HttpHeaders.CACHE_CONTROL, ARTICLE_PREVIEW_CACHE.getHeaderValue());
        return ARTICLE_PREVIEW_VIEW;
    }

    private Optional<ArticleSharePreview> findSharePreview(String articleId) {
        if (!ARTICLE_ID_PATTERN.matcher(articleId).matches()) {
            return Optional.empty();
        }
        return articleSharePreviewQueryService.findSharePreview(Long.parseLong(articleId));
    }
}
