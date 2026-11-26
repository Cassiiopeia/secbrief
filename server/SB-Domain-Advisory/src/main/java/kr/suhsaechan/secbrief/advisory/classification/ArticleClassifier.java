package kr.suhsaechan.secbrief.advisory.classification;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogEntry;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTermMatcher;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * 기사 분류 규칙 (카탈로그 스냅샷 기준, 순수 함수)
 * 제품: 글 순서대로(CPE > KEV > 패키지 > 제목 속 이름 > 제목) 처음 맞는 글에서 가장 긴 낱말
 * 분야: 제품의 분야 > 분야 강한 낱말 > 분야 약한 낱말 > AI 가 고른 분야 > other
 * 생태계: GitHub 권고 생태계 원문 값 일치 > other (패키지가 없으면 null)
 */
public final class ArticleClassifier {

    // 분류하지 않는 출처 2곳: KISA 경보단계·보고서 (제품이 없는 글)
    private static final Set<String> UNCLASSIFIED_SOURCE_CODES = Set.of(SourceCode.KISA_ALERT_LEVEL, SourceCode.KISA_REPORT);

    private ArticleClassifier() {
    }

    public static ArticleClassification classify(ArticleClassificationInput input, TagCatalog catalog) {
        if (UNCLASSIFIED_SOURCE_CODES.contains(input.sourceCode())) {
            return ArticleClassification.notClassified();
        }
        Optional<CatalogEntry> product = findBestMatch(catalog.findAllByKind(CatalogTagKind.PRODUCT), input.lowerCaseTexts(),
                (matcher, text) -> matcher.findLongestStrongMatchLength(text));
        String ecosystemCode = toEcosystemCode(input, catalog);

        // 분야 (제품 > 강한 낱말 > 약한 낱말 > AI > other)
        Optional<String> productFieldCode = product.map(CatalogEntry::fieldCode)
                .filter(fieldCode -> catalog.hasCode(CatalogTagKind.FIELD, fieldCode));
        if (productFieldCode.isPresent()) {
            return new ArticleClassification(productFieldCode.get(), ecosystemCode, product.get().code(),
                    input.packageName(), ClassificationSource.PRODUCT);
        }
        String productCode = product.map(CatalogEntry::code).orElse(null);
        List<CatalogEntry> fields = catalog.findAllByKind(CatalogTagKind.FIELD);
        Optional<CatalogEntry> keywordField = findBestMatch(fields, input.lowerCaseTexts(),
                (matcher, text) -> matcher.findLongestStrongMatchLength(text))
                .or(() -> findBestMatch(fields, input.lowerCaseTexts(),
                        (matcher, text) -> matcher.findLongestWeakMatchLength(text)));
        if (keywordField.isPresent()) {
            return new ArticleClassification(keywordField.get().code(), ecosystemCode, productCode,
                    input.packageName(), ClassificationSource.KEYWORD);
        }
        if (catalog.hasCode(CatalogTagKind.FIELD, input.aiFieldCode())) {
            return new ArticleClassification(input.aiFieldCode(), ecosystemCode, productCode, input.packageName(),
                    ClassificationSource.AI);
        }
        return new ArticleClassification(catalog.toOtherCodeOrNull(CatalogTagKind.FIELD), ecosystemCode, productCode,
                input.packageName(), ClassificationSource.NONE);
    }

    /** 규칙(제품·낱말)만으로 분야를 정했는지 (못 정했으면 AI 분야가 필요함) */
    public static boolean hasRuleField(ArticleClassification classification) {
        return classification.fieldSource() == ClassificationSource.PRODUCT
                || classification.fieldSource() == ClassificationSource.KEYWORD;
    }

    // 글 순서대로: 처음 맞는 글에서 가장 긴 일치 (같은 길이면 순서 앞선 항목)
    private static Optional<CatalogEntry> findBestMatch(List<CatalogEntry> entries, List<String> lowerCaseTexts,
                                                        ToIntBiFunction<CatalogTermMatcher, String> matchLength) {
        for (String text : lowerCaseTexts) {
            Optional<CatalogEntry> bestEntry = entries.stream()
                    .filter(entry -> matchLength.applyAsInt(entry.termMatcher(), text) > CatalogTermMatcher.NO_MATCH)
                    .max(Comparator.comparingInt((CatalogEntry entry) -> matchLength.applyAsInt(entry.termMatcher(), text))
                            .thenComparing(Comparator.comparingInt(CatalogEntry::displayOrder).reversed()));
            if (bestEntry.isPresent()) {
                return bestEntry;
            }
        }
        return Optional.empty();
    }

    // 패키지가 없으면 null, 생태계 원문이 어떤 항목에도 없으면 other
    private static String toEcosystemCode(ArticleClassificationInput input, TagCatalog catalog) {
        if (input.packageName() == null) {
            return null;
        }
        return catalog.findAllByKind(CatalogTagKind.ECOSYSTEM).stream()
                .filter(entry -> entry.termMatcher().hasExactTerm(input.rawEcosystem()))
                .map(CatalogEntry::code)
                .findFirst()
                .orElseGet(() -> catalog.toOtherCodeOrNull(CatalogTagKind.ECOSYSTEM));
    }
}
