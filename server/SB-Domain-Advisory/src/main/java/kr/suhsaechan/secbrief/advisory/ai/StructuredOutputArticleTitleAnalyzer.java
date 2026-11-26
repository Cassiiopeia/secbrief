package kr.suhsaechan.secbrief.advisory.ai;

import java.util.Collections;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 제목 분석 흐름 (제공자 공통): 프롬프트·스키마 만들기 > 제공자 호출(StructuredCompletionClient) > 응답 해석
 * 예외는 호출 실패(연결·상태 코드·제한 시간)뿐, 거부·읽을 수 없는 응답은 건별 실패 결과
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputArticleTitleAnalyzer implements ArticleTitleAnalyzer {

    // 설정으로 고른 제공자 (SelectedStructuredCompletionClient)
    private final StructuredCompletionClient structuredCompletionClient;

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
        StructuredCompletion completion = structuredCompletionClient.requestCompletion(new StructuredCompletionRequest(
                AiCallType.TITLE_ANALYSIS,
                ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION,
                ArticleTitleAnalysisPrompt.toUserMessage(requests),
                ArticleTitleAnalysisPrompt.toOutputJsonSchema(),
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

    private static List<ArticleTitleAnalysisOutcome> toAllFailed(int requestCount, String failureReason) {
        return Collections.nCopies(requestCount, ArticleTitleAnalysisOutcome.failed(failureReason));
    }
}
