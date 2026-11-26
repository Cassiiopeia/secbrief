package kr.suhsaechan.secbrief.advisory.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;

/**
 * 분류 태그 스냅샷 (보이는 항목만, 읽기 전용): 종류별 목록, code 찾기, 내용 판(version)
 * 판은 항목 값 전체의 해시 앞 12자 (바뀌면 앱이 다시 받음)
 */
public final class TagCatalog {

    // 분야·생태계를 못 정했을 때 쓰는 code (카탈로그에 없으면 null 로 둠)
    public static final String OTHER_CODE = "other";
    private static final int VERSION_LENGTH = 12;

    private final List<CatalogEntry> entries;
    private final String version;

    private TagCatalog(List<CatalogEntry> entries) {
        this.entries = entries.stream()
                .sorted(Comparator.comparing(CatalogEntry::kind)
                        .thenComparingInt(CatalogEntry::displayOrder)
                        .thenComparing(CatalogEntry::code))
                .toList();
        this.version = toVersion(this.entries);
    }

    public static TagCatalog of(List<CatalogEntry> entries) {
        return new TagCatalog(entries);
    }

    public static TagCatalog empty() {
        return new TagCatalog(List.of());
    }

    public String getVersion() {
        return version;
    }

    public List<CatalogEntry> getEntries() {
        return entries;
    }

    /** 종류별 항목 (순서대로) */
    public List<CatalogEntry> findAllByKind(CatalogTagKind kind) {
        return entries.stream().filter(entry -> entry.kind() == kind).toList();
    }

    public Optional<CatalogEntry> find(CatalogTagKind kind, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return entries.stream().filter(entry -> entry.kind() == kind && entry.code().equals(code)).findFirst();
    }

    public boolean hasCode(CatalogTagKind kind, String code) {
        return find(kind, code).isPresent();
    }

    /** 이름 표시: 짧은 이름 (모르는 code 면 비어 있음) */
    public Optional<String> findShortName(CatalogTagKind kind, String code) {
        return find(kind, code).map(CatalogEntry::shortName);
    }

    /** 못 정했을 때 code: other 가 보이면 other, 아니면 null */
    public String toOtherCodeOrNull(CatalogTagKind kind) {
        return hasCode(kind, OTHER_CODE) ? OTHER_CODE : null;
    }

    // 항목 값을 이어 붙여 해시 (순서 고정)
    private static String toVersion(List<CatalogEntry> entries) {
        StringBuilder content = new StringBuilder();
        for (CatalogEntry entry : entries) {
            content.append(entry.kind()).append('|').append(entry.code()).append('|').append(entry.name())
                    .append('|').append(entry.shortName()).append('|').append(entry.groupCode())
                    .append('|').append(entry.fieldCode()).append('|').append(entry.isWidelyUsed())
                    .append('|').append(entry.displayOrder()).append('|').append(entry.matchTerms()).append('\n');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, VERSION_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
