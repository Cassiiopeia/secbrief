package kr.suhsaechan.secbrief.advisory.collector;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * KISA 게시판 쪽 넘김: 범위보다 오래된 글이 나온 쪽에서 멈춤, 범위 없으면 마지막 쪽까지, 빈 쪽에서 멈춤, 쪽 요청 실패는 그대로 던짐
 */
class KisaBoardPageWalkerTest {

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final KisaBoardPageWalker walker = new KisaBoardPageWalker(feedHttpClient, Duration.ZERO);
    private final Source noticeSource = createSource(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);
    private final Source alertLevelSource =
            createSource(SourceCode.KISA_ALERT_LEVEL, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void stopsAtPageWhoseOldestRegularArticleIsOutOfRange() {
        when(feedHttpClient.fetch(KisaBoard.NOTICE.toListPageUrl(1))).thenReturn(KisaBoardListParserTest.readFixture("notice-page-1"));

        // 1쪽 일반 글은 11-20 ~ 11-26: 11-24 이후만 담고 2쪽은 읽지 않음 (고정 글은 범위 밖이라 빠짐)
        KisaBoardWalkResult walkResult = walker.collectSince(noticeSource, KisaBoard.NOTICE, Instant.parse("2026-11-23T15:00:00Z"));

        assertThat(walkResult.readPageCount()).isEqualTo(1);
        assertThat(walkResult.isPageLimitReached()).isFalse();
        assertThat(walkResult.articles()).isNotEmpty()
                .allSatisfy(article -> assertThat(article.publishedAt()).isAfterOrEqualTo(Instant.parse("2026-11-23T15:00:00Z")));
        verify(feedHttpClient, never()).fetch(KisaBoard.NOTICE.toListPageUrl(2));
    }

    @Test
    void walksWholeBoardWithoutRangeUntilLastPage() {
        when(feedHttpClient.fetch(anyString())).thenReturn(KisaBoardListParserTest.readFixture("alert-level-page-1"));
        when(feedHttpClient.fetch(KisaBoard.ALERT_LEVEL.toListPageUrl(7)))
                .thenReturn(KisaBoardListParserTest.readFixture("alert-level-page-7"));

        // 2~6쪽은 1쪽 내용으로 대신함 (같은 글은 한 번만): 1쪽 10건 + 7쪽 9건 (7쪽 고정 글은 1쪽 글)
        KisaBoardWalkResult walkResult = walker.collectSince(alertLevelSource, KisaBoard.ALERT_LEVEL, null);

        assertThat(walkResult.readPageCount()).isEqualTo(7);
        assertThat(walkResult.articles()).hasSize(19);
        assertThat(walkResult.articles().getFirst().publisherName()).isEqualTo(alertLevelSource.getDisplayName());
    }

    @Test
    void stopsAtEmptyPage() {
        when(feedHttpClient.fetch(anyString())).thenReturn(KisaBoardListParserTest.readFixture("vulnerability-page-19-empty"));

        KisaBoardWalkResult walkResult = walker.collectSince(noticeSource, KisaBoard.VULNERABILITY, null);

        assertThat(walkResult.readPageCount()).isEqualTo(1);
        assertThat(walkResult.articles()).isEmpty();
    }

    @Test
    void rethrowsPageFetchFailure() {
        when(feedHttpClient.fetch(anyString())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));

        assertThatThrownBy(() -> walker.collectSince(noticeSource, KisaBoard.NOTICE, null))
                .isInstanceOf(CustomException.class);
    }
}
