package kr.suhsaechan.secbrief.web.share;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.common.json.JsonSupport;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 공유 페이지 색: brand/brand-colors.json(빌드 시 classpath share/ 로 복사) > CSS 변수 (밝은 :root, 어두운 prefers-color-scheme)
 * 이름: accent > --sb-accent, surfaceRaised > --sb-surface-raised (light·dark 짝이 있는 값만)
 */
@Component
public class SharePageBrandColors {

    // SB-Web build.gradle processResources 가 레포 루트 brand/brand-colors.json 을 여기로 복사
    static final String BRAND_COLORS_RESOURCE = "share/brand-colors.json";
    private static final String LIGHT_KEY = "light";
    private static final String DARK_KEY = "dark";
    private static final String CSS_VARIABLE_PREFIX = "--sb-";
    // style 에 그대로 넣으므로 #RRGGBB 만 받음
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    private final String cssVariables;

    public SharePageBrandColors() {
        this.cssVariables = toCssVariables(readBrandColors());
    }

    /** <style> 에 넣을 변수 선언 (밝은 + 어두운) */
    public String getCssVariables() {
        return cssVariables;
    }

    private static JsonNode readBrandColors() {
        try (InputStream inputStream = new ClassPathResource(BRAND_COLORS_RESOURCE).getInputStream()) {
            return JsonSupport.OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new UncheckedIOException("[share page] brand colors not found : " + BRAND_COLORS_RESOURCE, exception);
        }
    }

    private static String toCssVariables(JsonNode brandColors) {
        StringBuilder lightDeclarations = new StringBuilder();
        StringBuilder darkDeclarations = new StringBuilder();
        for (Map.Entry<String, JsonNode> field : brandColors.properties()) {
            JsonNode colorPair = field.getValue();
            if (!isLightDarkColorPair(colorPair)) {
                continue;
            }
            String variableName = toCssVariableName(field.getKey());
            lightDeclarations.append(variableName).append(':').append(colorPair.get(LIGHT_KEY).asText()).append(';');
            darkDeclarations.append(variableName).append(':').append(colorPair.get(DARK_KEY).asText()).append(';');
        }
        return ":root{" + lightDeclarations + "}"
                + "@media (prefers-color-scheme: dark){:root{" + darkDeclarations + "}}";
    }

    private static boolean isLightDarkColorPair(JsonNode colorPair) {
        return colorPair.isObject()
                && HEX_COLOR_PATTERN.matcher(colorPair.path(LIGHT_KEY).asText()).matches()
                && HEX_COLOR_PATTERN.matcher(colorPair.path(DARK_KEY).asText()).matches();
    }

    // surfaceRaised > --sb-surface-raised
    private static String toCssVariableName(String brandColorKey) {
        return CSS_VARIABLE_PREFIX + CAMEL_CASE_BOUNDARY.matcher(brandColorKey).replaceAll("$1-$2").toLowerCase();
    }
}
