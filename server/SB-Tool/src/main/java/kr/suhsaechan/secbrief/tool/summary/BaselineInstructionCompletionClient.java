package kr.suhsaechan.secbrief.tool.summary;

import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;

/**
 * 평가 기준선 전용: 지시문만 하네스 이전의 단일 지시문(v0.6.1)으로 바꿔 넘김 (사용자 메시지·스키마는 그대로)
 * 기준선 지시문은 비교용으로 굳혀 둔 사본 (resources/summary-evaluation/baseline-system-instruction.txt): 운영에는 쓰지 않음
 */
public class BaselineInstructionCompletionClient implements StructuredCompletionClient {

    private static final String BASELINE_INSTRUCTION_RESOURCE = "summary-evaluation/baseline-system-instruction.txt";

    private final StructuredCompletionClient delegate;
    private final String baselineInstruction;

    public BaselineInstructionCompletionClient(StructuredCompletionClient delegate) {
        this.delegate = delegate;
        this.baselineInstruction = EvaluationResource.read(BASELINE_INSTRUCTION_RESOURCE);
    }

    @Override
    public String getProviderCode() {
        return delegate.getProviderCode();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        return delegate.requestCompletion(new StructuredCompletionRequest(request.callType(), baselineInstruction,
                request.userMessage(), request.outputJsonSchema(), request.maximumOutputTokens()));
    }
}
