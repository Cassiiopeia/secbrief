package kr.suhsaechan.secbrief.web.admin.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 관리자 동봉 라이브러리(/admin/vendor/**) 브라우저 캐시 30일
 * 보안 체인이 기본으로 붙이는 no-store 때문에 화면마다 큰 파일(CSS·JS 약 2MB)을 다시 받지 않게 함
 */
@Configuration
public class AdminStaticResourceConfig implements WebMvcConfigurer {

    private static final String VENDOR_PATH_PATTERN = "/admin/vendor/**";
    private static final String VENDOR_RESOURCE_LOCATION = "classpath:/static/admin/vendor/";
    // 파일명에 버전이 들어 있어 길게 잡음 (버전을 올리면 파일명이 바뀜)
    private static final Duration VENDOR_CACHE_DURATION = Duration.ofDays(30);

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(VENDOR_PATH_PATTERN)
                .addResourceLocations(VENDOR_RESOURCE_LOCATION)
                .setCacheControl(CacheControl.maxAge(VENDOR_CACHE_DURATION));
    }
}
