package kr.suhsaechan.secbrief.advisory.ai;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogEntry;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTermMatcher;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 제목 분석 흐름 (제공자 공통): 프롬프트·스키마 만들기(분야 목록은 호출마다 카탈로그에서) > 제공자 호출(StructuredCompletionClient) > 응답 해석
 * 예외는 호출 실패(연결·상태 코드·제한 시간)뿐, 거부·읽을 수 없는 응답은 건별 실패 결과
 */
@Slf4j
@Component
public class StructuredOutputArticleTitleAnalyzer implements ArticleTitleAnalyzer {

    // 지시문에 싣는 분야별 예시 낱말 수
    private static final int EXAMPLE_TERM_COUNT = 6;

    // 설정으로 고른 제공자 (SelectedStructuredCompletionClient)
    private final StructuredCompletionClient structuredCompletionClient;
    // 지금 카탈로그의 기술 분야 (관리자가 바꾸면 다음 호출부터 반영)
    private final Supplier<List<AiFieldOption>> fieldOptionsSupplier;

    @Autowired
    public StructuredOutputArticleTitleAnalyzer(StructuredCompletionClient structuredCompletionClient,
                                                TagCatalogProvider tagCatalogProvider) {
        this(structuredCompletionClient, () -> toFieldOptions(tagCatalogProvider));
    }

    /** 분야를 묻지 않는 분석기 (제공자 연동 시험) */
    public StructuredOutputArticleTitleAnalyzer(StructuredCompletionClient structuredCompletionClient) {
        this(structuredCompletionClient, List::of);
    }

    private StructuredOutputArticleTitleAnalyzer(StructuredCompletionClient structuredCompletionClient,
                                                 Supplier<List<AiFieldOption>> fieldOptionsSupplier) {
        this.structuredCompletionClient = structuredCompletionClient;
        this.fieldOptionsSupplier = fieldOptionsSupplier;
    }

    @Override
    public String getProviderCode() {
        return structuredCompletionClient.getProviderCode();
    }

    @Override
    public boolean isAvailable() {
        return structuredCompletionClient.isAvailable();
    }

    @Override
    public List<ArticleTitleAnalysisOutcome> analyzeBatch(List<ArticleTitleAnalysisRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        List<AiFieldOption> fieldOptions = fieldOptionsSupplier.get();
        StructuredCompletion completion = structuredCompletionClient.requestCompletion(new StructuredCompletionRequest(
                AiCallType.TITLE_ANALYSIS,
                ArticleTitleAnalysisPrompt.toSystemInstruction(fieldOptions),
                ArticleTitleAnalysisPrompt.toUserMessage(requests),
                ArticleTitleAnalysisPrompt.toOutputJsonSchema(fieldOptions.stream().map(AiFieldOption::code).toList()),
                ArticleTitleAnalysisPrompt.calculateMaximumOutputTokens(requests)));
        // 잘린 응답: 온전히 끝난 건만 쓰고 나머지는 건별 실패 (해석 단계에서 처리)
        if (completion.isTruncated()) {
            log.warn("[article ai] truncated : provider={}, requested={}", getProviderCode(), requests.size());
        }
        if (completion.isRefused()) {
            return toAllFailed(requests.size(), "refused");
        }
        try {
            return ArticleTitleAnalysisPrompt.toOutcomes(completion.responseJson(), requests.size());
        } catch (CustomException exception) {
            // 응답은 왔지만 JSON 이 아니거나 결과 배열이 없음 (제공자 장애가 아니라 이 묶음의 문제)
            return toAllFailed(requests.size(), "unreadable response");
        }
    }

    // 카탈로그 FIELD > 분야 선택지 (예시는 강한 낱말 앞 6개)
    private static List<AiFieldOption> toFieldOptions(TagCatalogProvider tagCatalogProvider) {
        return tagCatalogProvider.getCatalog().findAllByKind(CatalogTagKind.FIELD).stream()
                .map(entry -> new AiFieldOption(entry.code(), entry.name(), toExampleTerms(entry)))
                .toList();
    }

    private static String toExampleTerms(CatalogEntry entry) {
        return String.join(", ", Arrays.stream(entry.matchTerms().split(CatalogTermMatcher.TERM_DELIMITER))
                .map(String::strip)
                .filter(term -> !term.isEmpty() && !term.startsWith(CatalogTermMatcher.WEAK_TERM_PREFIX))
                .limit(EXAMPLE_TERM_COUNT)
                .toList());
    }

    private static List<ArticleTitleAnalysisOutcome> toAllFailed(int requestCount, String failureReason) {
        return Collections.nCopies(requestCount, ArticleTitleAnalysisOutcome.failed(failureReason));
    }
}
