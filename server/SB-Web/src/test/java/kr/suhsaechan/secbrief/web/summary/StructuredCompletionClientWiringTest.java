package kr.suhsaechan.secbrief.web.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.advisory.ai.SelectedStructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionClient;
import kr.suhsaechan.secbrief.advisory.ai.StructuredCompletionRequest;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 제공자 빈 연결 (스프링): 제목 분석·요약이 우선순위로 고른 제공자를 같이 씀, 키가 없으면 둘 다 건너뜀 (기동은 됨)
 */
@SpringBootTest
@ActiveProfiles("test")
class StructuredCompletionClientWiringTest extends PostgresContainerTestSupport {

    @Autowired
    private StructuredCompletionClient structuredCompletionClient;
    @Autowired
    private ArticleTitleAnalyzer articleTitleAnalyzer;
    @Autowired
    private ArticleSummarizer articleSummarizer;

    @Test
    void titleAnalysisAndSummaryShareSelectedProvider() {
        assertThat(structuredCompletionClient).isInstanceOf(SelectedStructuredCompletionClient.class);
        assertThat(articleTitleAnalyzer.getProviderCode()).isEqualTo("anthropic");
        assertThat(articleSummarizer.getProviderCode()).isEqualTo("anthropic");
        // 테스트 설정은 키가 비어 있음: 호출하지 않고 건너뜀
        assertThat(articleTitleAnalyzer.isAvailable()).isFalse();
        assertThat(articleSummarizer.isAvailable()).isFalse();
    }

    @Test
    void selectedClientFindsConfiguredProviderAmongProviderBeans() {
        // "unknown provider" 가 아니라 "provider not configured": 제공자 목록에서 anthropic 을 찾았다는 뜻
        assertThatThrownBy(() -> structuredCompletionClient.requestCompletion(
                new StructuredCompletionRequest(AiCallType.TITLE_ANALYSIS, "instruction", "{}", Map.of("type", "object"), 16)))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("provider not configured");
    }
}
