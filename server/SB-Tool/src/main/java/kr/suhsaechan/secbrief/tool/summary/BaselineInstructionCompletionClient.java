package kr.suhsaechan.secbrief.tool.summary;

import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;

/**
 * 평가 기준선 전용 (#49): 지시문만 하네스 이전의 단일 지시문(v0.6.1)으로 바꿔 넘김 (사용자 메시지·스키마는 그대로)
 * 기준선 지시문은 이전 구조(요약·핵심 정리·대응 안내)의 글이라 브리핑 구조(#67)의 출력 스키마와 맞지 않음: 지금은 어디서도 쓰지 않음
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
