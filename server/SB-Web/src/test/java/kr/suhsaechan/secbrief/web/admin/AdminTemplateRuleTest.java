package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 관리자 화면 템플릿 규칙 (소스 검사): 틀·제목 조각 사용, 화면마다 h1 1개, 좁은 화면 표 규칙, 인라인 스타일·색 금지,
 * th:replace 와 th:if 를 한 태그에 같이 쓰지 않음 (replace 가 먼저 처리돼 조건이 무시됨)
 */
class AdminTemplateRuleTest {

    // 테스트 작업 폴더는 SB-Web 모듈
    private static final Path ADMIN_TEMPLATE_DIRECTORY = Path.of("src/main/resources/templates/admin");
    // 틀(page)을 쓰지 않는 화면 1개: 로그인 (사이드바 없음, <head> 조각만 같이 씀)
    private static final Set<String> PAGES_WITHOUT_LAYOUT = Set.of("login.html");
    private static final String LAYOUT_CALL = "admin/fragments/admin-layout :: page(";
    private static final String PAGE_HEADER_CALL = "admin/fragments/admin-ui :: pageHeader(";
    private static final String STACK_TABLE_CLASS = "admin-stack-table";

    private static final Pattern TAG_PATTERN = Pattern.compile("<[a-zA-Z][^>]*>", Pattern.DOTALL);
    private static final Pattern TABLE_PATTERN = Pattern.compile("<table[^>]*>.*?</table>", Pattern.DOTALL);
    private static final Pattern TABLE_CELL_PATTERN = Pattern.compile("<td(\\s[^>]*)?>", Pattern.DOTALL);
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}\\b|#[0-9a-fA-F]{3}\\b");

    @Test
    void everyPageUsesLayoutAndPageHeader() throws IOException {
        for (Path pagePath : listPageTemplates()) {
            String template = read(pagePath);
            if (PAGES_WITHOUT_LAYOUT.contains(pagePath.getFileName().toString())) {
                continue;
            }
            assertThat(template).as(pagePath + " 틀(page) 사용").contains(LAYOUT_CALL);
            assertThat(template).as(pagePath + " 제목 조각(pageHeader) 사용").contains(PAGE_HEADER_CALL);
            // 화면 제목 h1 은 pageHeader 하나만 그림
            assertThat(template).as(pagePath + " h1 직접 작성").doesNotContain("<h1");
        }
    }

    @Test
    void everyTableStacksOnNarrowScreens() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path templatePath : listAllTemplates()) {
            Matcher tableMatcher = TABLE_PATTERN.matcher(read(templatePath));
            while (tableMatcher.find()) {
                String table = tableMatcher.group();
                String tableTag = table.substring(0, table.indexOf('>') + 1);
                if (!tableTag.contains(STACK_TABLE_CLASS)) {
                    violations.add(templatePath + " : " + tableTag);
                }
                collectUnlabeledCells(templatePath, table, violations);
            }
        }
        assertThat(violations).as("표는 admin-stack-table, 칸은 data-label (제목 칸은 admin-stack-primary)").isEmpty();
    }

    @Test
    void noInlineStyleOrHexColor() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path templatePath : listAllTemplates()) {
            Matcher tagMatcher = TAG_PATTERN.matcher(read(templatePath));
            while (tagMatcher.find()) {
                String tag = tagMatcher.group();
                if (tag.contains(" style=") || HEX_COLOR_PATTERN.matcher(tag).find()) {
                    violations.add(templatePath + " : " + tag);
                }
            }
        }
        assertThat(violations).as("색·간격은 daisyUI·Tailwind 클래스로").isEmpty();
    }

    @Test
    void replaceIsNotCombinedWithCondition() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path templatePath : listAllTemplates()) {
            Matcher tagMatcher = TAG_PATTERN.matcher(read(templatePath));
            while (tagMatcher.find()) {
                String tag = tagMatcher.group();
                boolean hasFragmentInclusion = tag.contains("th:replace=") || tag.contains("th:insert=");
                boolean hasCondition = tag.contains("th:if=") || tag.contains("th:unless=");
                if (hasFragmentInclusion && hasCondition) {
                    violations.add(templatePath + " : " + tag);
                }
            }
        }
        assertThat(violations).as("조건은 바깥 th:block 에").isEmpty();
    }

    // 칸 하나라도 data-label·admin-stack-primary·colspan 이 없으면 위반
    private static void collectUnlabeledCells(Path templatePath, String table, List<String> violations) {
        Matcher cellMatcher = TABLE_CELL_PATTERN.matcher(table);
        while (cellMatcher.find()) {
            String cellTag = cellMatcher.group();
            boolean isLabeled = cellTag.contains("data-label=") || cellTag.contains("admin-stack-primary")
                    || cellTag.contains("colspan");
            if (!isLabeled) {
                violations.add(templatePath + " : " + cellTag);
            }
        }
    }

    private static List<Path> listPageTemplates() throws IOException {
        try (Stream<Path> paths = Files.list(ADMIN_TEMPLATE_DIRECTORY)) {
            return paths.filter(path -> path.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static List<Path> listAllTemplates() throws IOException {
        try (Stream<Path> paths = Files.walk(ADMIN_TEMPLATE_DIRECTORY)) {
            return paths.filter(path -> path.toString().endsWith(".html")).sorted().toList();
        }
    }

    private static String read(Path templatePath) throws IOException {
        return Files.readString(templatePath, StandardCharsets.UTF_8);
    }
}
