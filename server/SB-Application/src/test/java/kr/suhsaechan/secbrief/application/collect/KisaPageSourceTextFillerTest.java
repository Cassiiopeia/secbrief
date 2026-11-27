package kr.suhsaechan.secbrief.application.collect;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * KISA 게시글 본문 > 요약 입력: 대상 조건 4개(켜짐, KISA 게시판 4곳, 요약 입력 없음), 못 받음·예외는 초안 그대로 (#57)
 */
class KisaPageSourceTextFillerTest {

    private static final String PAGE_TEXT = "□ 개요 o Example社 보안 업데이트 발표 [제품명: Example Server | 영향받는 버전: 5.2.3 미만]";
    private static final String ARTICLE_URL = "https://www.boho.or.kr/kr/bbs/view.do?bbsId=B0000302&nttId=1";

    private final SourceTextRefetcher sourceTextRefetcher = mock(SourceTextRefetcher.class);
    private final KisaPageSourceTextFiller enabledFiller = new KisaPageSourceTextFiller(sourceTextRefetcher, true);

    @Test
    void fillsEveryKisaBoardSource() {
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.recovered(PAGE_TEXT));

        for (String kisaSourceCode : new String[] {SourceCode.KISA, SourceCode.KISA_VULNERABILITY, SourceCode.KISA_REPORT,
                SourceCode.KISA_ALERT_LEVEL}) {
            assertThat(enabledFiller.fillSourceText(createKisaSource(kisaSourceCode), createDraft(null)).sourceText())
                    .isEqualTo(PAGE_TEXT);
        }
    }

    @Test
    void skipsWhenDisabledNotKisaOrAlreadyHasSourceText() {
        KisaPageSourceTextFiller disabledFiller = new KisaPageSourceTextFiller(sourceTextRefetcher, false);
        Source dailysecuSource = createSource(SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);
        Source kisaSource = createKisaSource(SourceCode.KISA);

        assertThat(disabledFiller.fillSourceText(kisaSource, createDraft(null)).sourceText()).isNull();
        assertThat(enabledFiller.fillSourceText(dailysecuSource, createDraft(null)).sourceText()).isNull();
        assertThat(enabledFiller.fillSourceText(kisaSource, createDraft("피드가 준 요약문")).sourceText()).isEqualTo("피드가 준 요약문");
        verify(sourceTextRefetcher, never()).refetch(any());
    }

    @Test
    void keepsDraftWhenPageIsNotRecoveredOrRequestFails() {
        Source kisaSource = createKisaSource(SourceCode.KISA_REPORT);
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.notFound("content not found"));
        assertThat(enabledFiller.fillSourceText(kisaSource, createDraft(null)).sourceText()).isNull();

        when(sourceTextRefetcher.refetch(any())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        assertThat(enabledFiller.fillSourceText(kisaSource, createDraft(null)).sourceText()).isNull();
    }

    private static Source createKisaSource(String sourceCode) {
        return createSource(sourceCode, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);
    }

    private static ArticleDraft createDraft(String sourceText) {
        return new ArticleDraft(ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, "Example 제품 보안 업데이트 권고", ARTICLE_URL, null,
                Instant.parse("2026-11-27T00:00:00Z"), false, null, null, null, false, false, null, sourceText, null);
    }
}
