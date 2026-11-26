package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 제공자 선택: 우선순위(AI_PROVIDER_PRIORITY) 순으로 쓸 수 있는 제공자에 위임, 호출 실패면 다음 제공자, 미설정·모르는 값은 건너뜀
 * 제목 분석 흐름: 지시문·제목 묶음·스키마·토큰 상한을 고른 제공자에 넘기고 응답을 건별 결과로 바꿈
 */
class SelectedStructuredCompletionClientTest {

    private static final List<ArticleTitleAnalysisRequest> REQUESTS =
            List.of(new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS));
    private static final String RESPONSE_JSON = "{\"results\":[{\"number\":1,\"topic\":\"remoteAccess\",\"translatedTitle\":null}]}";
    private static final StructuredCompletionRequest REQUEST = new StructuredCompletionRequest(
            AiCallType.TITLE_ANALYSIS, "instruction", "{}", Map.of("type", "object"), 16);

    private final StubProviderClient firstProvider = new StubProviderClient("first", true);
    private final StubProviderClient secondProvider = new StubProviderClient("second", true);
    private final StubProviderClient unconfiguredProvider = new StubProviderClient("unconfigured", false);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);

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
        assertThat(completionRequest.callType()).isEqualTo(AiCallType.TITLE_ANALYSIS);
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
    void selectedClientPassesRequestThroughAndReturnsProviderCompletion() {
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first");

        assertThat(selectedClient.requestCompletion(REQUEST)).isEqualTo(StructuredCompletion.of(RESPONSE_JSON, false));
        assertThat(firstProvider.receivedRequests).containsExactly(REQUEST);
    }

    @Test
    void usesFirstProviderInPriorityOrderAndDoesNotCallTheRest() {
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("second,first");

        selectedClient.requestCompletion(REQUEST);

        assertThat(selectedClient.getProviderCode()).isEqualTo("second,first");
        assertThat(secondProvider.receivedRequests).hasSize(1);
        assertThat(firstProvider.receivedRequests).isEmpty();
    }

    @Test
    void fallsBackToNextProviderWhenCallFails() {
        firstProvider.failCalls();
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first,second");

        assertThat(selectedClient.requestCompletion(REQUEST)).isEqualTo(StructuredCompletion.of(RESPONSE_JSON, false));
        assertThat(firstProvider.receivedRequests).hasSize(1);
        assertThat(secondProvider.receivedRequests).hasSize(1);
    }

    @Test
    void skipsUnconfiguredAndUnknownProvidersInPriority() {
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("unconfigured, nobody ,second");

        assertThat(selectedClient.isAvailable()).isTrue();
        selectedClient.requestCompletion(REQUEST);

        assertThat(unconfiguredProvider.receivedRequests).isEmpty();
        assertThat(secondProvider.receivedRequests).hasSize(1);
    }

    @Test
    void throwsLastFailureWhenEveryProviderFails() {
        firstProvider.failCalls();
        secondProvider.failCalls();
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first,second");

        assertThatThrownBy(() -> selectedClient.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("second failed")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(firstProvider.receivedRequests).hasSize(1);
        assertThat(secondProvider.receivedRequests).hasSize(1);
    }

    @Test
    void doesNotFallBackWhenProviderRefusesOrTruncates() {
        // 거부·잘림은 호출 성공: 다음 제공자로 넘기지 않음
        firstProvider.respondWith(StructuredCompletion.refused());
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first,second");

        assertThat(selectedClient.requestCompletion(REQUEST).isRefused()).isTrue();
        assertThat(secondProvider.receivedRequests).isEmpty();
    }

    @Test
    void readsPriorityFromConfigStoreOnEveryCall() {
        SelectedStructuredCompletionClient selectedClient = createSelectedClient("first");
        selectedClient.requestCompletion(REQUEST);

        // 관리자 화면에서 우선순위를 바꾼 뒤: 서버를 다시 띄우지 않고 반영
        when(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).thenReturn("second");
        selectedClient.requestCompletion(REQUEST);

        assertThat(firstProvider.receivedRequests).hasSize(1);
        assertThat(secondProvider.receivedRequests).hasSize(1);
    }

    @Test
    void distinguishesUnknownProviderFromUnconfiguredProvider() {
        assertThatThrownBy(() -> createSelectedClient("nobody").requestCompletion(REQUEST))
                .hasMessageContaining("unknown provider");
        assertThatThrownBy(() -> createSelectedClient("unconfigured").requestCompletion(REQUEST))
                .hasMessageContaining("provider not configured");
    }

    // 운영과 같은 조합: 제목 분석 흐름 + 우선순위로 고른 제공자
    private ArticleTitleAnalyzer createAnalyzer(String priorityValue) {
        return new StructuredOutputArticleTitleAnalyzer(createSelectedClient(priorityValue));
    }

    private SelectedStructuredCompletionClient createSelectedClient(String priorityValue) {
        when(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).thenReturn(priorityValue);
        return new SelectedStructuredCompletionClient(
                List.of(firstProvider, secondProvider, unconfiguredProvider), systemConfigService);
    }

    /** 테스트용 제공자: 받은 요청을 기록하고 정해 둔 응답을 돌려줌 (failCalls 뒤에는 호출 실패) */
    private static final class StubProviderClient implements StructuredCompletionClient {

        private final String providerCode;
        private final boolean isAvailable;
        private final List<StructuredCompletionRequest> receivedRequests = new ArrayList<>();
        private StructuredCompletion completion = StructuredCompletion.of(RESPONSE_JSON, false);
        private boolean isFailing;

        private StubProviderClient(String providerCode, boolean isAvailable) {
            this.providerCode = providerCode;
            this.isAvailable = isAvailable;
        }

        private void failCalls() {
            this.isFailing = true;
        }

        private void respondWith(StructuredCompletion completion) {
            this.completion = completion;
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
            if (isFailing) {
                throw new CustomException(ErrorCode.AI_API_ERROR, providerCode + " failed");
            }
            return completion;
        }
    }
}
