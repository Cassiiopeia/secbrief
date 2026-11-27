package kr.suhsaechan.secbrief.advisory.classification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.collector.NvdResponseParser;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;

/**
 * 분류 입력 (공개 값만): 출처 코드, 확인 순서대로 고른 글 목록(CPE 제품 > KEV 제조사·제품 > 패키지 이름 > 제목 속 이름 > 제목), 첫 패키지·생태계 원문, AI 분야
 */
public record ArticleClassificationInput(String sourceCode, List<String> lowerCaseTexts, String packageName,
                                         String rawEcosystem, String aiFieldCode) {

    // GHSA 영향 패키지 값 모양: "이름 (생태계)" (GhsaAdvisoryParser)
    private static final Pattern PACKAGE_WITH_ECOSYSTEM = Pattern.compile("^(.+?)\\s*\\(([^()]+)\\)$");

    /** 기사 > 분류 입력 (본문은 쓰지 않음) */
    public static ArticleClassificationInput from(Article article) {
        return of(article.getSource().getCode(), article.getTitle(), article.getSubjectName(),
                article.getSourceFacts(), article.getClassificationState().getAiFieldCode());
    }

    /** 값 > 분류 입력 (사실 값에서 CPE·KEV 제품·패키지를 꺼냄) */
    public static ArticleClassificationInput of(String sourceCode, String title, String subjectName,
                                                List<SourceFact> sourceFacts, String aiFieldCode) {
        List<String> cpeProducts = new ArrayList<>();
        List<String> kevVendorProducts = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        String firstPackageName = null;
        String firstRawEcosystem = null;
        for (SourceFact sourceFact : sourceFacts) {
            switch (sourceFact.label()) {
                case SourceFactLabel.AFFECTED_PRODUCT ->
                        cpeProducts.addAll(List.of(sourceFact.value().split(NvdResponseParser.AFFECTED_PRODUCT_SEPARATOR)));
                case SourceFactLabel.VENDOR_PRODUCT -> kevVendorProducts.add(sourceFact.value());
                case SourceFactLabel.AFFECTED_PACKAGE -> {
                    Matcher matcher = PACKAGE_WITH_ECOSYSTEM.matcher(sourceFact.value().strip());
                    String packageName = matcher.matches() ? matcher.group(1).strip() : sourceFact.value().strip();
                    packageNames.add(packageName);
                    if (firstPackageName == null) {
                        firstPackageName = packageName;
                        firstRawEcosystem = matcher.matches() ? matcher.group(2).strip() : null;
                    }
                }
                default -> {
                }
            }
        }
        List<String> texts = new ArrayList<>();
        texts.addAll(cpeProducts);
        texts.addAll(kevVendorProducts);
        texts.addAll(packageNames);
        if (subjectName != null) {
            texts.add(subjectName);
        }
        texts.add(title == null ? "" : title);
        return new ArticleClassificationInput(sourceCode,
                texts.stream().map(text -> text.strip().toLowerCase(Locale.ROOT)).filter(text -> !text.isEmpty()).toList(),
                firstPackageName, firstRawEcosystem, aiFieldCode);
    }
}
