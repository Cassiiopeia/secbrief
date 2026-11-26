package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletion;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * claude -p 호출: 도구·MCP·설정 없이 부르는 옵션, 사용자 메시지는 표준 입력, 빈 작업 폴더, 결과 JSON 해석
 * 실제 CLI 대신 받은 인자·입력을 파일에 적고 정해 둔 결과를 내는 가짜 실행 파일을 씀 (외부 호출 없음)
 */
class ClaudePrintCompletionClientTest {

    private static final StructuredCompletionRequest REQUEST = new StructuredCompletionRequest(AiCallType.SUMMARY,
            "system instruction with \"quotes\"\nand a second line", "{\"title\":\"Example\"}",
            Map.of("type", "object"), 2500);

    @TempDir
    private Path temporaryDirectory;

    @Test
    void runsPrintModeWithoutToolsMcpOrSettingsAndPassesUserMessageOnStandardInput() throws Exception {
        ClaudePrintCompletionClient client = createClient(
                "{\"is_error\":false,\"stop_reason\":\"tool_use\",\"structured_output\":{\"summary\":\"요약이에요.\"}}");

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.responseJson()).isEqualTo("{\"summary\":\"요약이에요.\"}");
        assertThat(completion.isTruncated()).isFalse();
        assertThat(completion.isRefused()).isFalse();
        List<String> receivedArguments = Files.readAllLines(temporaryDirectory.resolve("arguments.txt"), UTF_8);
        assertThat(receivedArguments).containsSubsequence("--print", "--model", "claude-haiku-5-5", "--output-format", "json");
        assertThat(receivedArguments).contains("--safe-mode", "--strict-mcp-config", "--disable-slash-commands",
                "--no-session-persistence");
        // 도구 없음: --tools 다음 인자가 빈 문자열
        assertThat(receivedArguments.get(receivedArguments.indexOf("--tools") + 1)).isEmpty();
        assertThat(receivedArguments.get(receivedArguments.indexOf("--effort") + 1)).isEqualTo("low");
        assertThat(receivedArguments.get(receivedArguments.indexOf("--json-schema") + 1)).isEqualTo("{\"type\":\"object\"}");
        assertThat(Files.readString(temporaryDirectory.resolve("system-prompt.txt"), UTF_8)).isEqualTo(REQUEST.systemInstruction());
        assertThat(Files.readString(temporaryDirectory.resolve("standard-input.txt"), UTF_8)).isEqualTo(REQUEST.userMessage());
        // 저장소 밖 빈 폴더에서 실행 (프로젝트 CLAUDE.md 를 읽지 않음)
        assertThat(Path.of(Files.readString(temporaryDirectory.resolve("working-directory.txt"), UTF_8).strip()).toRealPath())
                .isEqualTo(temporaryDirectory.resolve("empty").toRealPath());
    }

    @Test
    void readsTruncatedRefusedAndTextOnlyResults() throws Exception {
        assertThat(createClient("{\"is_error\":false,\"stop_reason\":\"max_tokens\",\"structured_output\":{\"summary\":\"끊\"}}")
                .requestCompletion(REQUEST).isTruncated()).isTrue();
        assertThat(createClient("{\"is_error\":false,\"stop_reason\":\"refusal\",\"result\":\"\"}")
                .requestCompletion(REQUEST).isRefused()).isTrue();
        // 스키마에 맞는 출력이 없으면 글 응답을 그대로 넘김 (호출부가 읽을 수 없는 응답으로 처리)
        assertThat(createClient("{\"is_error\":false,\"stop_reason\":\"end_turn\",\"result\":\"I cannot help\"}")
                .requestCompletion(REQUEST).responseJson()).isEqualTo("I cannot help");
    }

    @Test
    void throwsAiApiErrorWhenClaudeReportsErrorOrPrintsNothing() throws Exception {
        assertThatThrownBy(() -> createClient("{\"is_error\":true,\"api_error_status\":429}").requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThatThrownBy(() -> createClient("").requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    // 가짜 실행 파일: 인자를 한 줄씩, --system-prompt 값·표준 입력·작업 폴더를 파일에 적고 정해 둔 JSON 을 출력
    private ClaudePrintCompletionClient createClient(String printResultJson) throws IOException {
        Path emptyDirectory = Files.createDirectories(temporaryDirectory.resolve("empty"));
        Path resultFile = temporaryDirectory.resolve("result.json");
        Files.writeString(resultFile, printResultJson, UTF_8);
        Path fakeBinary = temporaryDirectory.resolve("fake-claude.sh");
        Files.writeString(fakeBinary, """
                #!/bin/sh
                out="%s"
                : > "$out/arguments.txt"
                previous=""
                for argument in "$@"; do
                  if [ "$previous" = "--system-prompt" ]; then
                    printf '%%s' "$argument" > "$out/system-prompt.txt"
                    echo "<system-prompt>" >> "$out/arguments.txt"
                  else
                    echo "$argument" >> "$out/arguments.txt"
                  fi
                  previous="$argument"
                done
                cat > "$out/standard-input.txt"
                pwd > "$out/working-directory.txt"
                cat "$out/result.json"
                """.formatted(temporaryDirectory), UTF_8);
        Files.setPosixFilePermissions(fakeBinary, PosixFilePermissions.fromString("rwx------"));
        return new ClaudePrintCompletionClient(fakeBinary.toString(), "claude-haiku-5-5", emptyDirectory);
    }
}
