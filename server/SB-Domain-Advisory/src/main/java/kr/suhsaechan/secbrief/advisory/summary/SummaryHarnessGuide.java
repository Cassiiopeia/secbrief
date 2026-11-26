package kr.suhsaechan.secbrief.advisory.summary;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 요약 작성 규칙 파일 읽기 (요약 하네스의 단일 원본): 공통 규칙 1개 + 글 종류별 안내 6개 + 고쳐 쓰기 규칙 1개
 * 위치: resources/summary-harness/{이름}.md, 처음 읽은 글을 보관해 다시 읽지 않음 (파일이 없으면 IllegalStateException)
 */
public final class SummaryHarnessGuide {

    private static final String RESOURCE_DIRECTORY = "summary-harness/";
    private static final String RESOURCE_EXTENSION = ".md";
    private static final String COMMON_RULES_NAME = "common-rules";
    private static final String WRITING_KIND_PREFIX = "kind-";
    private static final String REPAIR_RULES_NAME = "repair-rules";

    private static final Map<String, String> GUIDE_TEXT_BY_NAME = new ConcurrentHashMap<>();

    private SummaryHarnessGuide() {
    }

    /** 모든 글에 공통인 규칙: 입력 다루기, 근거 규칙, 문체, 출력 필드 */
    public static String commonRules() {
        return read(COMMON_RULES_NAME);
    }

    /** 글 종류별 안내: 담을 내용, 조심할 점, 좋은 예·나쁜 예 */
    public static String writingKindGuide(SummaryWritingKind writingKind) {
        return read(WRITING_KIND_PREFIX + writingKind.getGuideName());
    }

    /** 검증에 걸린 초안을 고쳐 쓰는 규칙: 거부 사유마다 고치는 법 */
    public static String repairRules() {
        return read(REPAIR_RULES_NAME);
    }

    private static String read(String guideName) {
        return GUIDE_TEXT_BY_NAME.computeIfAbsent(guideName, SummaryHarnessGuide::load);
    }

    private static String load(String guideName) {
        String resourcePath = RESOURCE_DIRECTORY + guideName + RESOURCE_EXTENSION;
        try (InputStream guideStream = SummaryHarnessGuide.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (guideStream == null) {
                throw new IllegalStateException("summary harness guide is missing : " + resourcePath);
            }
            return new String(guideStream.readAllBytes(), UTF_8).strip();
        } catch (IOException exception) {
            throw new IllegalStateException("summary harness guide is not readable : " + resourcePath, exception);
        }
    }
}
