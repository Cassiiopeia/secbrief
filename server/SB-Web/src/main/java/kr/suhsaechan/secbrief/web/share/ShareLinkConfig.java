package kr.suhsaechan.secbrief.web.share;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 공유 링크 설정 등록 + 미리보기 정적 파일(/share/**) 브라우저 캐시 7일
 */
@Configuration
@EnableConfigurationProperties(ShareLinkProperties.class)
public class ShareLinkConfig implements WebMvcConfigurer {

    private static final String STATIC_RESOURCE_LOCATION = "classpath:/static/share/";
    // 아이콘은 자주 바뀌지 않음 (바뀌어도 일주일 안에 반영)
    private static final Duration STATIC_CACHE_DURATION = Duration.ofDays(7);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(SharePaths.STATIC_PATTERN)
                .addResourceLocations(STATIC_RESOURCE_LOCATION)
                .setCacheControl(CacheControl.maxAge(STATIC_CACHE_DURATION).cachePublic());
    }
}
