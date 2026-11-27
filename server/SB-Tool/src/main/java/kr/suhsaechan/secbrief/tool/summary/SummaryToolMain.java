package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import kr.suhsaechan.secbrief.advisory.summary.StructuredOutputArticleSummarizer;
import lombok.extern.slf4j.Slf4j;

/**
 * 요약 로컬 도구 진입점: 명령 7개 (recover, generate, apply-file, sample, evaluate, judge, evaluation-report)
 * 적재(브리핑 만들기) 실행 순서는 SB-Tool/run-summary-backfill.sh, 평가 실행 순서는 docs/summary-harness/evaluation.md
 * 요약 입력이 든 파일은 저장소 밖 임시 폴더에 두고 끝나면 지움
 */
@Slf4j
public final class SummaryToolMain {

    // 생성 모델: 운영과 같은 값 (secbrief.ai.model 기본값). 다른 모델로 요약을 만들지 않음
    private static final String GENERATION_MODEL = "claude-haiku-5-5";
    private static final String DEFAULT_CLAUDE_BINARY = "claude";
    // 동시 호출 수 기본값 (claude -p 1회 5~10초, 4개면 분당 30건 안팎: 구독 사용량 제한에 여유)
    private static final String DEFAULT_CONCURRENCY = "4";
    private static final String OPTION_PREFIX = "--";

    private SummaryToolMain() {
    }

    public static void main(String[] arguments) {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("command is required : recover, generate, apply-file, sample, evaluate, judge, evaluation-report");
        }
        Map<String, String> options = toOptions(arguments);
        switch (arguments[0]) {
            case "recover" -> recover(options);
            case "generate" -> generate(options);
            case "apply-file" -> writeApplyFile(options);
            case "sample" -> writeSample(options);
            case "evaluate" -> evaluate(options);
            case "judge" -> judge(options);
            case "evaluation-report" -> writeEvaluationReport(options);
            default -> throw new IllegalArgumentException("unknown command : " + arguments[0]);
        }
    }

    // 입력 없는 대상 > 공개 출처에서 되찾기 > 대상 전체를 --output 에
    // (되찾은 행은 source_text, 내용 없는 입력은 정리만 한 글: generate 가 INPUT_TOO_THIN 으로 셈, 못 찾은 행은 사유)
    // --prefer-ipv4: 출처 주소의 IPv4 먼저 (운영 secbrief.feed-http.prefer-ipv4 와 같은 기본값)
    private static void recover(Map<String, String> options) {
        List<JsonNode> targetRows = JsonLinesFile.readAll(toPath(options, "targets"));
        List<BackfillTarget> targetsWithoutSourceText = targetRows.stream().map(BackfillTarget::fromJson)
                .filter(target -> !target.hasSourceText()).toList();
        FeedHttpClient feedHttpClient = new FeedHttpClient(Boolean.parseBoolean(
                options.getOrDefault("prefer-ipv4", String.valueOf(FeedHttpClient.SHOULD_PREFER_IPV4_BY_DEFAULT))));
        Clock clock = Clock.systemUTC();
        Map<Long, SourceTextRefetchResult> resultByTargetId =
                new SourceTextRecoverer(feedHttpClient, clock, new SourceTextRefetcher(feedHttpClient, clock))
                        .recover(targetsWithoutSourceText);
        try (JsonLinesFile.Appender outputAppender = JsonLinesFile.openAppender(toPath(options, "output"))) {
            for (JsonNode targetRow : targetRows) {
                outputAppender.append(toRecoveredRow(targetRow, resultByTargetId.get(targetRow.path("id").asLong())));
            }
        }
        log.info("[summary backfill] recover finished : targets={}, withoutInput={}, recovered={}",
                targetRows.size(), targetsWithoutSourceText.size(),
                resultByTargetId.values().stream().filter(SourceTextRefetchResult::isRecovered).count());
    }

    // 입력이 있던 행은 그대로, 받은 글이 있으면 source_text, 없으면 사유
    private static JsonNode toRecoveredRow(JsonNode targetRow, SourceTextRefetchResult result) {
        if (result == null) {
            return targetRow;
        }
        return result.sourceText() != null
                ? BackfillTarget.withSourceText(targetRow, result.sourceText())
                : BackfillTarget.withRecoveryFailureReason(targetRow, result.failureReason());
    }

    // 대상(입력 포함) > 브리핑 > --results (JSON Lines) + --report (마크다운)
    private static void generate(Map<String, String> options) {
        Path resultsFile = toPath(options, "results");
        ClaudePrintCompletionClient generationClient = toGenerationClient(options);
        new SummaryBackfillGenerator(new StructuredOutputArticleSummarizer(generationClient), toConcurrency(options))
                .generate(readTargets(options, "inputs"), resultsFile);
        log.info("[summary backfill] token usage : {}", generationClient.toTokenUsageSummary());
        writeText(toPath(options, "report"), BackfillReport.toMarkdown(readResults(resultsFile)));
    }

    private static void writeApplyFile(Map<String, String> options) {
        writeText(toPath(options, "output"), SummaryApplyFileWriter.toApplySql(readResults(toPath(options, "results"))));
    }

    // 입력이 든 대상 > 출처·글 종류별로 고르게 --per-group 건씩
    private static void writeSample(Map<String, String> options) {
        List<JsonNode> sampleRows = EvaluationSampler.sample(JsonLinesFile.readAll(toPath(options, "inputs")),
                Integer.parseInt(options.get("per-group")));
        try (JsonLinesFile.Appender sampleAppender = JsonLinesFile.openAppender(toPath(options, "output"))) {
            sampleRows.forEach(sampleAppender::append);
        }
        log.info("[summary evaluation] sample written : count={}", sampleRows.size());
    }

    // 지금의 작성 규칙으로 표본마다 요약 시도 1회 (작성 + 고쳐 쓰기). --variant 는 출력 줄과 보고서에 적히는 이름표
    // 이전 판과 비교: 그 판의 커밋에서 같은 표본으로 만든 출력 파일을 함께 채점 (docs/summary-harness/evaluation.md)
    private static void evaluate(Map<String, String> options) {
        ClaudePrintCompletionClient generationClient = toGenerationClient(options);
        new SummaryEvaluationRunner(new StructuredOutputArticleSummarizer(generationClient),
                ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT, toConcurrency(options))
                .run(readTargets(options, "sample"), options.get("variant"), toPath(options, "output"));
        log.info("[summary evaluation] token usage : {}", generationClient.toTokenUsageSummary());
    }

    // --judge-model: 생성 모델보다 강한 모델 이름 (채점 전용)
    private static void judge(Map<String, String> options) {
        StructuredCompletionClient judgeClient = new ClaudePrintCompletionClient(
                options.getOrDefault("claude", DEFAULT_CLAUDE_BINARY), options.get("judge-model"), toPath(options, "empty-directory"));
        new SummaryJudge(judgeClient, toConcurrency(options)).judge(readTargets(options, "sample"),
                JsonLinesFile.readAll(toPath(options, "outputs")), toPath(options, "scores"));
    }

    private static void writeEvaluationReport(Map<String, String> options) {
        writeText(toPath(options, "output"), SummaryEvaluationReport.toMarkdown(options.get("variant"),
                readTargets(options, "sample"), JsonLinesFile.readAll(toPath(options, "outputs")),
                options.containsKey("scores") ? JsonLinesFile.readAll(toPath(options, "scores")) : List.of()));
    }

    private static ClaudePrintCompletionClient toGenerationClient(Map<String, String> options) {
        return new ClaudePrintCompletionClient(options.getOrDefault("claude", DEFAULT_CLAUDE_BINARY), GENERATION_MODEL,
                toPath(options, "empty-directory"));
    }

    private static int toConcurrency(Map<String, String> options) {
        return Integer.parseInt(options.getOrDefault("concurrency", DEFAULT_CONCURRENCY));
    }

    private static List<BackfillTarget> readTargets(Map<String, String> options, String optionName) {
        return JsonLinesFile.readAll(toPath(options, optionName)).stream().map(BackfillTarget::fromJson).toList();
    }

    private static List<BackfillResult> readResults(Path resultsFile) {
        return JsonLinesFile.readAll(resultsFile).stream().map(BackfillResult::fromJson).toList();
    }

    private static Path toPath(Map<String, String> options, String optionName) {
        String optionValue = options.get(optionName);
        if (optionValue == null) {
            throw new IllegalArgumentException("option is required : --" + optionName);
        }
        return Path.of(optionValue);
    }

    // 명령 뒤의 "--이름 값" 쌍
    private static Map<String, String> toOptions(String[] arguments) {
        Map<String, String> options = new HashMap<>();
        for (int index = 1; index + 1 < arguments.length; index += 2) {
            if (!arguments[index].startsWith(OPTION_PREFIX)) {
                throw new IllegalArgumentException("option name expected : " + arguments[index]);
            }
            options.put(arguments[index].substring(OPTION_PREFIX.length()), arguments[index + 1]);
        }
        return options;
    }

    private static void writeText(Path file, String text) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, text, UTF_8);
            log.info("[summary tool] wrote : file={}", file.getFileName());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
