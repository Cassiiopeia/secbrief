package kr.suhsaechan.secbrief.advisory.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogEntry;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;

/**
 * 시험용 카탈로그: V0.9.0 마이그레이션의 catalog_tag 시드 행을 그대로 읽어 스냅샷으로 (운영 시드와 같은 값으로 분류 규칙을 시험)
 */
public final class CatalogSeedFixtures {

    // 모듈 작업 폴더 기준 시드 파일 (SB-Domain-Advisory, SB-Application 모두 SB-Web 과 같은 단계)
    private static final Path SEED_MIGRATION = Path.of(
            "../SB-Web/src/main/resources/db/migration/V0.9.0__add_tag_catalog_interest_alerts_digest.sql");
    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "insert into catalog_tag \\(([^)]*)\\) values(.*?);\\s*$", Pattern.DOTALL | Pattern.MULTILINE);

    private CatalogSeedFixtures() {
    }

    public static TagCatalog seedCatalog() {
        String sql;
        try {
            sql = Files.readString(SEED_MIGRATION, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        List<CatalogEntry> entries = new ArrayList<>();
        Matcher insertMatcher = INSERT_PATTERN.matcher(sql);
        while (insertMatcher.find()) {
            List<String> columns = List.of(insertMatcher.group(1).replace(" ", "").split(","));
            for (List<String> values : toTuples(insertMatcher.group(2))) {
                entries.add(CatalogEntry.of(CatalogTagKind.valueOf(value(columns, values, "kind")),
                        value(columns, values, "code"), value(columns, values, "name"), value(columns, values, "short_name"),
                        value(columns, values, "group_code"), value(columns, values, "field_code"),
                        "true".equals(value(columns, values, "is_widely_used")),
                        Integer.parseInt(value(columns, values, "display_order")), value(columns, values, "match_terms")));
            }
        }
        return TagCatalog.of(entries);
    }

    private static String value(List<String> columns, List<String> values, String column) {
        int index = columns.indexOf(column);
        if (index < 0) {
            return switch (column) {
                case "is_widely_used" -> "false";
                case "display_order" -> "0";
                default -> null;
            };
        }
        String value = values.get(index);
        return "null".equals(value) ? null : value;
    }

    // ('a', 'b', 10, null, true) 묶음들 > 값 목록 (작은따옴표 글자는 따옴표를 벗김)
    private static List<List<String>> toTuples(String valuesText) {
        List<List<String>> tuples = new ArrayList<>();
        List<String> current = null;
        StringBuilder token = new StringBuilder();
        boolean isInQuote = false;
        boolean wasQuoted = false;
        for (int index = 0; index < valuesText.length(); index++) {
            char character = valuesText.charAt(index);
            if (isInQuote) {
                if (character == '\'' && index + 1 < valuesText.length() && valuesText.charAt(index + 1) == '\'') {
                    token.append('\'');
                    index++;
                } else if (character == '\'') {
                    isInQuote = false;
                } else {
                    token.append(character);
                }
            } else if (character == '\'') {
                isInQuote = true;
                wasQuoted = true;
            } else if (character == '(') {
                current = new ArrayList<>();
                token.setLength(0);
            } else if ((character == ',' || character == ')') && current != null) {
                current.add(wasQuoted ? token.toString() : token.toString().strip());
                token.setLength(0);
                wasQuoted = false;
                if (character == ')') {
                    tuples.add(current);
                    current = null;
                }
            } else if (current != null && !(Character.isWhitespace(character) && token.isEmpty())) {
                token.append(character);
            }
        }
        return tuples;
    }
}
