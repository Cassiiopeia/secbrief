package kr.suhsaechan.secbrief.application.notification;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.advisory.entity.Article;

/**
 * 아침 요약 규칙: 기사 묶음 > 분야별 건수(많은 순) > 알림 글 1건 ("오늘 아침 요약: 관심 분야 소식 N건", 분야 짧은 이름 + 건수)
 * 분야가 없거나 카탈로그에서 지운 분야는 other 로 셈
 */
public final class MorningDigestPolicy {

    // 아침 요약 대기분 보관 시간 (넘으면 버림: 이틀 전 소식을 아침 요약으로 보내지 않음)
    public static final Duration QUEUE_RETENTION_DURATION = Duration.ofHours(48);
    // 본문에 싣는 분야 수
    static final int MAXIMUM_BODY_FIELD_COUNT = 4;
    private static final String FIELD_SEPARATOR = " · ";

    private MorningDigestPolicy() {
    }

    public static UrgentAlertContent toContent(List<Article> articles, TagCatalog catalog) {
        String body = toFieldCountByCode(articles, catalog).entrySet().stream()
                .limit(MAXIMUM_BODY_FIELD_COUNT)
                .map(fieldCount -> catalog.findShortName(CatalogTagKind.FIELD, fieldCount.getKey()).orElse(fieldCount.getKey())
                        + " " + fieldCount.getValue())
                .collect(Collectors.joining(FIELD_SEPARATOR));
        return new UrgentAlertContent("오늘 아침 요약: 관심 분야 소식 " + articles.size() + "건",
                body + " — 눌러서 한눈에 봐요", null, articles.size());
    }

    /** 분야 code > 건수 (건수 많은 순, 같으면 카탈로그 순서) */
    public static Map<String, Long> toFieldCountByCode(List<Article> articles, TagCatalog catalog) {
        Map<String, Long> countByCode = articles.stream()
                .collect(Collectors.groupingBy(article -> toFieldCode(article, catalog), Collectors.counting()));
        List<String> catalogOrder = catalog.findAllByKind(CatalogTagKind.FIELD).stream().map(entry -> entry.code()).toList();
        return countByCode.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Long> entry) -> entry.getValue()).reversed()
                        .thenComparingInt(entry -> catalogOrder.indexOf(entry.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first,
                        LinkedHashMap::new));
    }

    /** 기사 > 분야 code (없거나 지운 분야면 other) */
    public static String toFieldCode(Article article, TagCatalog catalog) {
        return catalog.hasCode(CatalogTagKind.FIELD, article.getFieldCode()) ? article.getFieldCode() : TagCatalog.OTHER_CODE;
    }
}
