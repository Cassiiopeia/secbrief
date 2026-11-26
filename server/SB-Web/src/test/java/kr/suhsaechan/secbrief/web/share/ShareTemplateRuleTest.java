package kr.suhsaechan.secbrief.web.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 공유 미리보기 화면 규칙: 색 값 직접 사용 금지 (brand/brand-colors.json 의 --sb-* 변수만)
 */
class ShareTemplateRuleTest {

    private static final Path SHARE_TEMPLATE_DIRECTORY = Path.of("src/main/resources/templates/share");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b");

    @Test
    void shareTemplatesUseBrandColorVariablesOnly() throws IOException {
        try (Stream<Path> templatePaths = Files.walk(SHARE_TEMPLATE_DIRECTORY)) {
            List<String> violations = templatePaths
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> HEX_COLOR_PATTERN.matcher(readTemplate(path)).find())
                    .map(Path::toString)
                    .toList();
            assertThat(violations).as("색 값 직접 사용").isEmpty();
        }
    }

    private static String readTemplate(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
