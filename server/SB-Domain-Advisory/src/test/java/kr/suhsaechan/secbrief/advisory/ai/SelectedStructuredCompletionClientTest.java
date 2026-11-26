package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 제공자 선택: 설정값과 코드가 같은 제공자에 위임, 미설정 제공자·모르는 값은 건너뜀 (기동은 계속)
 * 제목 분석 흐름: 지시문·제목 묶음·스키마·토큰 상한을 고른 제공자에 넘기고 응답을 건별 결과로 바꿈
 */
class SelectedStructuredCompletionClientTest {

    private static final List<ArticleTitleAnalysisRequest> REQUESTS =
            List.of(new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS));
    private static final String RESPONSE_JSON = "{\"results\":[{\"number\":1,\"topic\":\"remoteAccess\",\"translatedTitle\":null}]}";

    private final StubProviderClient firstProvider = new StubProviderClient("first", true);
    private final StubProviderClient secondProvider = new StubProviderClient("second", true);
    private final StubProviderClient unconfiguredProvider = new StubProviderClient("unconfigured", false);

    @Test
    void delegatesToProviderMatchingConfiguredCode() {
        ArticleTitleAnalyzer analyzer = createAnalyzer(" Second ");

        List<ArticleTitleAnalysisOutcome> outcomes = analyzer.analyzeBatch(REQUESTS);

        assertThat(analyzer.isAvailable()).isTrue();
        assertThat(outcomes).containsExactly(ArticleTitleAnalysisOutcome.succeeded(
                new ArticleTitleAnalysis(ArticleTopic.REMOTE_ACCESS, null, ArticleImportance.ROUTINE, null)));
        assertThat(firstProvider.receivedRequests).isEmpty();
        assertThat(secondProvider.receivedRequests).hasSize(1);
    }

    @Test
    void passesSharedPromptSchemaAndTokenLimitToProvider() {
        createAnalyzer("first").analyzeBatch(REQUESTS);

        StructuredCompletionRequest completionRequest = firstProvider.receivedRequests.get(0);
        assertThat(completionRequest.systemInstruction()).isEqualTo(ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION);
        assertThat(completionRequest.userMessage()).isEqualTo(ArticleTitleAnalysisPrompt.toUserMessage(REQUESTS));
        assertThat(completionRequest.outputJsonSchema()).isEqualTo(ArticleTitleAnalysisPrompt.toOutputJsonSchema());
        assertThat(completionRequest.maximumOutputTokens())
                .isEqualTo(ArticleTitleAnalysisPrompt.calculateMaximumOutputTokens(REQUESTS));
    }

    @Test
    void isUnavailableWhenSelectedProviderIsNotConfigured() {
        ArticleTitleAnalyzer analyzer = createAnalyzer("unconfigured");

        assertThat(analyzer.isAvailable()).isFalse();
        assertThatThrownBy(() -> analyzer.analyzeBatch(REQUESTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(unconfiguredProvider.receivedRequests).isEmpty();
    }

    @Test
    void isUnavailableWithoutFailingWhenProviderCodeIsUnknown() {
        ArticleTitleAnalyzer analyzer = createAnalyzer("open ai\n");

        assertThat(analyzer.isAvailable()).isFalse();
        assertThat(analyzer.getProviderCode()).isEqualTo("open?ai");
        assertThatThrownBy(() -> analyzer.analyzeBatch(REQUESTS)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void doesNotCallProviderForEmptyBatch() {
        assertThat(createAnalyzer("first").analyzeBatch(List.of())).isEmpty();
        assertThat(firstProvider.receivedRequests).isEmpty();
    }

    @Test
    void realProvidersHaveDistinctCodesAndAreUnavailableWithoutSettings() {
        AnthropicStructuredCompletionClient anthropicClient = new AnthropicStructuredCompletionClient("", "claude-haiku-5-5");
        OllamaStructuredCompletionClient ollamaClient = new OllamaStructuredCompletionClient("", "", "");
        List<StructuredCompletionClient> providerClients = List.of(anthropicClient, ollamaClient);

        assertThat(anthropicClient.getProviderCode()).isEqualTo("anthropic");
        assertThat(ollamaClient.getProviderCode()).isEqualTo("ollama");
        assertThat(new SelectedStructuredCompletionClient(providerClients, "anthropic").isAvailable()).isFalse();
        assertThat(new SelectedStructuredCompletionClient(providerClients, "ollama").isAvailable()).isFalse();
        assertThatThrownBy(() -> anthropicClient.requestCompletion(
                new StructuredCompletionRequest("instruction", "{}", java.util.Map.of("type", "object"), 16)))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void selectedClientPassesRequestThroughAndReturnsProviderCompletion() {
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first");
        StructuredCompletionRequest request =
                new StructuredCompletionRequest("instruction", "{}", java.util.Map.of("type", "object"), 16);

        assertThat(selectedClient.requestCompletion(request)).isEqualTo(StructuredCompletion.of(RESPONSE_JSON, false));
        assertThat(firstProvider.receivedRequests).containsExactly(request);
    }

    // 운영과 같은 조합: 제목 분석 흐름 + 설정으로 고른 제공자
    private ArticleTitleAnalyzer createAnalyzer(String configuredProviderCode) {
        return new StructuredOutputArticleTitleAnalyzer(createSelectedClient(configuredProviderCode));
    }

    private SelectedStructuredCompletionClient createSelectedClient(String configuredProviderCode) {
        return new SelectedStructuredCompletionClient(
                List.of(firstProvider, secondProvider, unconfiguredProvider), configuredProviderCode);
    }

    /** 테스트용 제공자: 받은 요청을 기록하고 정해 둔 응답 JSON 을 돌려줌 */
    private static final class StubProviderClient implements StructuredCompletionClient {

        private final String providerCode;
        private final boolean isAvailable;
        private final List<StructuredCompletionRequest> receivedRequests = new ArrayList<>();

        private StubProviderClient(String providerCode, boolean isAvailable) {
            this.providerCode = providerCode;
            this.isAvailable = isAvailable;
        }

        @Override
        public String getProviderCode() {
            return providerCode;
        }

        @Override
        public boolean isAvailable() {
            return isAvailable;
        }

        @Override
        public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
            receivedRequests.add(request);
            return StructuredCompletion.of(RESPONSE_JSON, false);
        }
    }
}
