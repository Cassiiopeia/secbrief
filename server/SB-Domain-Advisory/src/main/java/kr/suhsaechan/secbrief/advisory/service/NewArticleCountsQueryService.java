package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.NewArticleCountsResponse;
import kr.suhsaechan.secbrief.advisory.dto.NewArticleCountsResponse.SourceCount;
import kr.suhsaechan.secbrief.advisory.dto.NewArticleCountsResponse.TagCount;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.NewArticleTagRow;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 새로 들어온 소식 (홈 띠·취약점 칩 +N, #93): 기기 날짜 0시 이후 수집분 > 건수 묶음 (수집한 공개 소식만 셈)
 * 분야·생태계는 카탈로그에 보이는 code 만, 뉴스는 따로 셈
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewArticleCountsQueryService {

    // 대표 이름 수
    static final int SAMPLE_NAME_COUNT = 3;

    private final ArticleRepository articleRepository;
    private final TagCatalogProvider tagCatalogProvider;
    private final Clock clock;

    /** timeZone: IANA 이름 (모르면 INVALID_TIME_ZONE) */
    public NewArticleCountsResponse findTodayCounts(String timeZone) {
        Instant since = toStartOfToday(timeZone);
        List<NewArticleTagRow> rows = articleRepository.findNewArticleTagRows(since);
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        List<NewArticleTagRow> nonNewsRows = rows.stream().filter(row -> row.kind() != ArticleKind.NEWS).toList();

        Map<String, Integer> countBySourceCode = new LinkedHashMap<>();
        rows.forEach(row -> countBySourceCode.merge(row.sourceCode(), 1, Integer::sum));
        return new NewArticleCountsResponse(since,
                rows.isEmpty() ? null : rows.get(0).collectedAt(),
                rows.size(),
                (int) rows.stream().filter(NewArticleTagRow::isKev).count(),
                (int) rows.stream().filter(row -> SourceCode.isKisa(row.sourceCode())).count(),
                rows.size() - nonNewsRows.size(),
                toTagCounts(nonNewsRows, CatalogTagKind.FIELD, NewArticleTagRow::fieldCode, catalog),
                toTagCounts(nonNewsRows, CatalogTagKind.ECOSYSTEM, NewArticleTagRow::ecosystemCode, catalog),
                countBySourceCode.entrySet().stream()
                        .map(entry -> new SourceCount(entry.getKey(), entry.getValue()))
                        .toList());
    }

    private Instant toStartOfToday(String timeZone) {
        try {
            ZoneId zoneId = ZoneId.of(timeZone == null || timeZone.isBlank() ? "Asia/Seoul" : timeZone);
            return clock.instant().atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant();
        } catch (DateTimeException exception) {
            throw new CustomException(ErrorCode.INVALID_TIME_ZONE);
        }
    }

    // 카탈로그 순서대로, 0건과 모르는 code 는 뺌
    private static List<TagCount> toTagCounts(List<NewArticleTagRow> rows, CatalogTagKind kind,
                                              Function<NewArticleTagRow, String> codeOf, TagCatalog catalog) {
        List<TagCount> tagCounts = new ArrayList<>();
        catalog.findAllByKind(kind).forEach(entry -> {
            List<NewArticleTagRow> matchedRows = rows.stream().filter(row -> entry.code().equals(codeOf.apply(row))).toList();
            if (!matchedRows.isEmpty()) {
                tagCounts.add(new TagCount(entry.code(), matchedRows.size(), toSampleNames(matchedRows, catalog)));
            }
        });
        return tagCounts;
    }

    // 제품 짧은 이름 > 패키지 이름 > 제목 속 이름 (중복 없이 앞 3개)
    private static List<String> toSampleNames(List<NewArticleTagRow> rows, TagCatalog catalog) {
        Set<String> sampleNames = new LinkedHashSet<>();
        for (NewArticleTagRow row : rows) {
            String sampleName = catalog.findShortName(CatalogTagKind.PRODUCT, row.productCode())
                    .orElse(row.packageName() != null ? row.packageName() : row.subjectName());
            if (sampleName != null && !sampleName.isBlank()) {
                sampleNames.add(sampleName);
            }
            if (sampleNames.size() >= SAMPLE_NAME_COUNT) {
                break;
            }
        }
        return List.copyOf(sampleNames);
    }
}
