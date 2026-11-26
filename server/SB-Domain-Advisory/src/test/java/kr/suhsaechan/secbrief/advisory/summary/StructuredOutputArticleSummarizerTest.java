package kr.suhsaechan.secbrief.advisory.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.ai.OllamaStructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 요약 흐름: 지시문·기사 JSON·스키마·토큰 상한을 제공자에 넘기고 응답을 결과로 바꿈
 * 거부·잘림·읽을 수 없는 응답은 실패 결과, 미설정·호출 실패만 예외 (Ollama 는 로컬 서버, 외부 호출 없음)
 */
class StructuredOutputArticleSummarizerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ArticleSummaryRequest REQUEST = new ArticleSummaryRequest("Example VPN flaw exploited",
            ArticleRegion.OVERSEAS, ArticleKind.NEWS, SummaryWritingKind.NEWS_ARTICLE,
            "Attackers are exploiting a flaw in Example VPN to steal session tokens.");
    private static final String RESPONSE_JSON =
            "{\"summary\":\"Example VPN 결함이 악용되고 있어요.\",\"keyPoints\":[\"세션 토큰을 노려요.\"],\"actionGuide\":null}";
    private static final ArticleSummary SUMMARY =
            new ArticleSummary("Example VPN 결함이 악용되고 있어요.", List.of("세션 토큰을 노려요."), null);

    @Test
    void passesSummaryPromptSchemaAndTokenLimitToProvider() {
        StubCompletionClient client = new StubCompletionClient(StructuredCompletion.of(RESPONSE_JSON, false));

        ArticleSummaryOutcome outcome = new StructuredOutputArticleSummarizer(client).summarize(REQUEST);

        assertThat(outcome).isEqualTo(ArticleSummaryOutcome.succeeded(SUMMARY));
        StructuredCompletionRequest completionRequest = client.receivedRequests.get(0);
        assertThat(completionRequest.callType()).isEqualTo(AiCallType.SUMMARY);
        assertThat(completionRequest.systemInstruction())
                .isEqualTo(ArticleSummaryPrompt.toSystemInstruction(SummaryWritingKind.NEWS_ARTICLE));
        assertThat(completionRequest.userMessage()).isEqualTo(ArticleSummaryPrompt.toUserMessage(REQUEST));
        assertThat(completionRequest.outputJsonSchema()).isEqualTo(ArticleSummaryPrompt.toOutputJsonSchema());
        assertThat(completionRequest.maximumOutputTokens()).isEqualTo(ArticleSummaryPrompt.MAXIMUM_OUTPUT_TOKENS);
    }

    @Test
    void repairSendsRepairInstructionAndDraftWithProblemsInOneCall() {
        StubCompletionClient client = new StubCompletionClient(StructuredCompletion.of(RESPONSE_JSON, false));
        ArticleSummary rejectedDraft = new ArticleSummary("Example VPN 결함 2개가 악용되고 있어요.", List.of(), null);
        List<SummaryProblem> problems =
                List.of(new SummaryProblem("summary", ArticleSummaryPolicy.REASON_NUMBER_NOT_IN_SOURCE, "2"));

        ArticleSummaryOutcome outcome = new StructuredOutputArticleSummarizer(client).repair(REQUEST, rejectedDraft, problems);

        assertThat(outcome).isEqualTo(ArticleSummaryOutcome.succeeded(SUMMARY));
        assertThat(client.receivedRequests).hasSize(1);
        StructuredCompletionRequest completionRequest = client.receivedRequests.get(0);
        assertThat(completionRequest.callType()).isEqualTo(AiCallType.SUMMARY);
        assertThat(completionRequest.systemInstruction())
                .isEqualTo(ArticleSummaryPrompt.toRepairSystemInstruction(SummaryWritingKind.NEWS_ARTICLE));
        assertThat(completionRequest.userMessage())
                .isEqualTo(ArticleSummaryPrompt.toRepairUserMessage(REQUEST, rejectedDraft, problems));
        assertThat(completionRequest.outputJsonSchema()).isEqualTo(ArticleSummaryPrompt.toOutputJsonSchema());
        assertThat(completionRequest.maximumOutputTokens()).isEqualTo(ArticleSummaryPrompt.MAXIMUM_OUTPUT_TOKENS);
    }

    @Test
    void returnsFailedOutcomeWhenRefusedTruncatedOrUnreadable() {
        assertThat(summarizeWith(StructuredCompletion.refused()).failureReason()).isEqualTo("refused");
        // 잘린 응답은 JSON 이 우연히 닫혀 있어도 쓰지 않음
        assertThat(summarizeWith(StructuredCompletion.of(RESPONSE_JSON, true)).failureReason()).isEqualTo("truncated");
        assertThat(summarizeWith(StructuredCompletion.of("", false)).failureReason()).isEqualTo("unreadable response");
    }

    @Test
    void throwsAiApiErrorWithoutCallingWhenProviderIsNotConfigured() {
        StubCompletionClient client = new StubCompletionClient(StructuredCompletion.of(RESPONSE_JSON, false));
        client.isAvailable = false;
        StructuredOutputArticleSummarizer summarizer = new StructuredOutputArticleSummarizer(client);

        assertThat(summarizer.isAvailable()).isFalse();
        assertThatThrownBy(() -> summarizer.summarize(REQUEST)).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(client.receivedRequests).isEmpty();
    }

    @Test
    void letsProviderCallFailurePropagate() {
        StubCompletionClient client = new StubCompletionClient(null);

        assertThatThrownBy(() -> new StructuredOutputArticleSummarizer(client).summarize(REQUEST))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void summarizesThroughOllamaChatApi() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/chat", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            byte[] responseBytes = OBJECT_MAPPER.writeValueAsBytes(Map.of(
                    "message", Map.of("role", "assistant", "content", RESPONSE_JSON), "done", true, "done_reason", "stop"));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
        try {
            OllamaStructuredCompletionClient ollamaClient = new OllamaStructuredCompletionClient(
                    mock(AiCallLogService.class), "http://127.0.0.1:" + server.getAddress().getPort(), "test-model", "");

            ArticleSummaryOutcome outcome = new StructuredOutputArticleSummarizer(ollamaClient).summarize(REQUEST);

            assertThat(outcome).isEqualTo(ArticleSummaryOutcome.succeeded(SUMMARY));
            JsonNode chatRequest = OBJECT_MAPPER.readTree(receivedBody.get());
            assertThat(chatRequest.get("format")).isEqualTo(OBJECT_MAPPER.valueToTree(ArticleSummaryPrompt.toOutputJsonSchema()));
            assertThat(chatRequest.get("options").get("num_predict").asInt()).isEqualTo(ArticleSummaryPrompt.MAXIMUM_OUTPUT_TOKENS);
            assertThat(chatRequest.get("messages").get(1).get("content").asText())
                    .isEqualTo(ArticleSummaryPrompt.toUserMessage(REQUEST));
        } finally {
            server.stop(0);
        }
    }

    private static ArticleSummaryOutcome summarizeWith(StructuredCompletion completion) {
        return new StructuredOutputArticleSummarizer(new StubCompletionClient(completion)).summarize(REQUEST);
    }

    /** 테스트용 제공자: 받은 요청을 기록하고 정해 둔 응답을 돌려줌 (응답이 null 이면 호출 실패) */
    private static final class StubCompletionClient implements StructuredCompletionClient {

        private final StructuredCompletion completion;
        private final List<StructuredCompletionRequest> receivedRequests = new ArrayList<>();
        private boolean isAvailable = true;

        private StubCompletionClient(StructuredCompletion completion) {
            this.completion = completion;
        }

        @Override
        public String getProviderCode() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return isAvailable;
        }

        @Override
        public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
            receivedRequests.add(request);
            if (completion == null) {
                throw new CustomException(ErrorCode.AI_API_ERROR, "stub call failure");
            }
            return completion;
        }
    }
}
