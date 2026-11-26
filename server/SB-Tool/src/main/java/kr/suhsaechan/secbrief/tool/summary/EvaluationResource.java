package kr.suhsaechan.secbrief.tool.summary;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * 평가용 글 파일 읽기 (resources/summary-evaluation/): 기준선 지시문, 채점 지시문 (파일이 없으면 IllegalStateException)
 */
final class EvaluationResource {

    private EvaluationResource() {
    }

    static String read(String resourcePath) {
        try (InputStream resourceStream = EvaluationResource.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (resourceStream == null) {
                throw new IllegalStateException("evaluation resource is missing : " + resourcePath);
            }
            return new String(resourceStream.readAllBytes(), UTF_8).strip();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
