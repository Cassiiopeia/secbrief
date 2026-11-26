package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.common.json.JsonSupport.OBJECT_MAPPER;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 로컬 Claude Code CLI 호출 (claude -p): 지시문 + 사용자 메시지(표준 입력) + JSON 스키마 > 응답 JSON
 * 도구·MCP·설정·훅·CLAUDE.md 없이 실행 (--safe-mode, --tools "", --strict-mcp-config, 빈 작업 폴더), 세션을 디스크에 남기지 않음
 * 운영 제공자 목록에는 넣지 않는 로컬 도구 전용 구현 (출력 토큰 상한은 CLI 에 옵션이 없어 적용하지 않음)
 * 운영 API 와 다른 점: CLI 는 생각(adaptive thinking)을 끌 수 없음 (운영은 생각 끔). 호출마다 답·생각 토큰 수를 세어 둠
 */
public class ClaudePrintCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = "claude-print";
    // 호출 1회 제한 시간 (초): 요약 1건은 10초 안팎, 멈춘 프로세스가 작업 스레드를 붙잡지 않게 함
    private static final long CALL_TIMEOUT_SECONDS = 180;
    // 운영 API 호출과 같은 노력 수준 (AnthropicStructuredCompletionClient: effort low). 생각은 이 값과 무관하게 켜져 있음
    private static final String EFFORT_LEVEL = "low";
    // 부모 Claude Code 세션이 물려준 환경변수 접두사: 자식 호출이 부모 세션에 묶이지 않게 지움 (로그인 위치 CLAUDE_CONFIG_DIR 은 유지)
    private static final List<String> INHERITED_SESSION_VARIABLE_PREFIXES =
            List.of("CLAUDE_CODE_", "CLAUDECODE", "CLAUDE_PID", "CLAUDE_EFFORT");
    // 실행 결과 JSON 의 stop_reason 값
    private static final String STOP_REASON_MAX_TOKENS = "max_tokens";
    private static final String STOP_REASON_REFUSAL = "refusal";

    private final String claudeBinaryPath;
    private final String model;
    private final Path emptyWorkingDirectory;
    // 토큰 사용량 (여러 작업 스레드가 함께 씀): 호출 수, 답 토큰(출력 - 생각)의 합·최댓값, 생각 토큰의 합
    private final AtomicInteger callCount = new AtomicInteger();
    private final AtomicLong answerTokenCount = new AtomicLong();
    private final AtomicInteger maximumAnswerTokenCount = new AtomicInteger();
    private final AtomicLong thinkingTokenCount = new AtomicLong();

    /**
     * emptyWorkingDirectory: 저장소 밖 빈 폴더 (CLI 가 프로젝트 CLAUDE.md·설정을 읽지 않게 함)
     */
    public ClaudePrintCompletionClient(String claudeBinaryPath, String model, Path emptyWorkingDirectory) {
        this.claudeBinaryPath = claudeBinaryPath;
        this.model = model;
        this.emptyWorkingDirectory = emptyWorkingDirectory;
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * 프로세스 실행 > 결과 JSON 해석. 실행 실패·시간 초과·실행 오류(is_error)는 AI_API_ERROR (입력 글은 메시지에 넣지 않음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        JsonNode printResult = runClaudeProcess(request);
        if (printResult == null || !printResult.isObject()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "claude print returned no result");
        }
        if (printResult.path("is_error").asBoolean(false)) {
            throw new CustomException(ErrorCode.AI_API_ERROR,
                    "claude print failed : status=" + printResult.path("api_error_status").asText("unknown"));
        }
        recordTokenUsage(printResult.path("usage"));
        String stopReason = printResult.path("stop_reason").asText("");
        if (STOP_REASON_REFUSAL.equals(stopReason)) {
            return StructuredCompletion.refused();
        }
        JsonNode structuredOutput = printResult.path("structured_output");
        // 스키마에 맞는 출력이 없으면 글 응답을 그대로 넘겨 호출부가 읽을 수 없는 응답으로 처리
        String responseJson = structuredOutput.isObject() ? structuredOutput.toString() : printResult.path("result").asText("");
        return StructuredCompletion.of(responseJson, STOP_REASON_MAX_TOKENS.equals(stopReason));
    }

    /**
     * 지금까지의 토큰 사용량 한 줄 (로그용): 답 토큰은 운영 출력 토큰 상한과 견줄 값, 생각 토큰은 운영(생각 끔)에는 없는 값
     */
    public String toTokenUsageSummary() {
        int calls = Math.max(callCount.get(), 1);
        return "calls=" + callCount.get() + ", averageAnswerTokens=" + answerTokenCount.get() / calls
                + ", maximumAnswerTokens=" + maximumAnswerTokenCount.get()
                + ", averageThinkingTokens=" + thinkingTokenCount.get() / calls;
    }

    // usage.output_tokens 는 생각 토큰을 포함: 답 토큰 = 출력 - 생각 (구조화 출력의 도구 호출 틀 포함)
    private void recordTokenUsage(JsonNode usage) {
        int thinkingTokens = usage.path("output_tokens_details").path("thinking_tokens").asInt(0);
        int answerTokens = Math.max(usage.path("output_tokens").asInt(0) - thinkingTokens, 0);
        callCount.incrementAndGet();
        answerTokenCount.addAndGet(answerTokens);
        thinkingTokenCount.addAndGet(thinkingTokens);
        maximumAnswerTokenCount.accumulateAndGet(answerTokens, Math::max);
    }

    private JsonNode runClaudeProcess(StructuredCompletionRequest request) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(toCommand(request));
            processBuilder.directory(emptyWorkingDirectory.toFile());
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
            processBuilder.environment().keySet().removeIf(ClaudePrintCompletionClient::isInheritedSessionVariable);
            Process process = processBuilder.start();
            // 출력을 따로 읽어 파이프가 차서 멈추는 일을 막음
            CompletableFuture<byte[]> standardOutput = CompletableFuture.supplyAsync(() -> readAll(process));
            try (OutputStream standardInput = process.getOutputStream()) {
                standardInput.write(request.userMessage().getBytes(UTF_8));
            }
            if (!process.waitFor(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new CustomException(ErrorCode.AI_API_ERROR, "claude print timed out");
            }
            return OBJECT_MAPPER.readTree(standardOutput.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.AI_API_ERROR, "claude print interrupted");
        } catch (IOException | ExecutionException | TimeoutException exception) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "claude print unreadable : " + exception.getClass().getSimpleName());
        }
    }

    // 명령: 출력 1건(JSON), 도구·MCP·사용자 설정·슬래시 명령 없음, 세션 저장 없음
    List<String> toCommand(StructuredCompletionRequest request) throws IOException {
        return List.of(claudeBinaryPath,
                "--print",
                "--model", model,
                "--output-format", "json",
                "--safe-mode",
                "--tools", "",
                "--strict-mcp-config",
                "--disable-slash-commands",
                "--no-session-persistence",
                "--effort", EFFORT_LEVEL,
                "--system-prompt", request.systemInstruction(),
                "--json-schema", OBJECT_MAPPER.writeValueAsString(request.outputJsonSchema()));
    }

    private static boolean isInheritedSessionVariable(String variableName) {
        return INHERITED_SESSION_VARIABLE_PREFIXES.stream().anyMatch(variableName::startsWith);
    }

    private static byte[] readAll(Process process) {
        try {
            return process.getInputStream().readAllBytes();
        } catch (IOException exception) {
            return new byte[0];
        }
    }
}
