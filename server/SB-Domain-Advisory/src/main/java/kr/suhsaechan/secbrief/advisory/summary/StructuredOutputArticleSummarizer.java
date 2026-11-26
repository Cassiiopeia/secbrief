package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 요약 흐름 (제공자 공통): 프롬프트·스키마 만들기 > 제공자 호출(StructuredCompletionClient) > 응답 해석
 * 작성과 고쳐 쓰기는 지시문·사용자 메시지만 다름. 한 호출에 기사 1건 (출력이 길어 묶으면 잘림·기사 간 사실 섞임 위험), 예외는 호출 실패뿐
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredOutputArticleSummarizer implements ArticleSummarizer {

    // 설정으로 고른 제공자 (제목 분석과 같은 구현)
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
    public ArticleSummaryOutcome summarize(ArticleSummaryRequest request) {
        return requestSummary(ArticleSummaryPrompt.toSystemInstruction(request.writingKind()),
                ArticleSummaryPrompt.toUserMessage(request));
    }

    @Override
    public ArticleSummaryOutcome repair(ArticleSummaryRequest request, ArticleSummary rejectedDraft,
                                        List<SummaryProblem> problems) {
        return requestSummary(ArticleSummaryPrompt.toRepairSystemInstruction(request.writingKind()),
                ArticleSummaryPrompt.toRepairUserMessage(request, rejectedDraft, problems));
    }

    private ArticleSummaryOutcome requestSummary(String systemInstruction, String userMessage) {
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        StructuredCompletion completion = structuredCompletionClient.requestCompletion(new StructuredCompletionRequest(
                AiCallType.SUMMARY,
                systemInstruction,
                userMessage,
                ArticleSummaryPrompt.toOutputJsonSchema(),
                ArticleSummaryPrompt.MAXIMUM_OUTPUT_TOKENS));
        if (completion.isRefused()) {
            return ArticleSummaryOutcome.failed("refused");
        }
        // 잘린 응답: 끊긴 요약을 싣지 않음 (JSON 도 닫히지 않음)
        if (completion.isTruncated()) {
            log.warn("[article summary] truncated : provider={}", getProviderCode());
            return ArticleSummaryOutcome.failed("truncated");
        }
        return ArticleSummaryPrompt.toOutcome(completion.responseJson());
    }
}
