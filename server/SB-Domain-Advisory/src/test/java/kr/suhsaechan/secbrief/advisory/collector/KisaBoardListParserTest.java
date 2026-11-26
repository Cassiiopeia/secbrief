package kr.suhsaechan.secbrief.advisory.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * KISA 게시판 목록 파싱: 실제 목록 쪽 4곳(목록 표·쪽 번호만 남긴 fixture)의 제목·글 주소·게시일, 고정 글 구분, 다음 쪽 여부
 * 글 주소는 RSS link 와 같은 모양 (쪽 번호 1), 취약점 게시판의 심각도·CVSS 칸은 게시일로 읽지 않음
 */
class KisaBoardListParserTest {

    private static final String PUBLISHER = "KISA 보호나라";

    @Test
    void parsesNoticePageWithPinnedRowsAndRssShapedLinks() {
        KisaBoardPage page = parseFixture("notice-page-1", KisaBoard.NOTICE, 1);

        assertThat(page.pinnedArticles()).hasSize(5);
        assertThat(page.regularArticles()).hasSize(10);
        CollectedArticle first = page.regularArticles().getFirst();
        assertThat(first.title()).isEqualTo("Linux 제품 보안 업데이트 권고");
        // RSS 피드 link 와 글자까지 같아야 원문 링크 중복 확인이 맞음
        assertThat(first.originalUrl()).isEqualTo("https://www.boho.or.kr/kr/bbs/view.do?searchCnd=&bbsId=B0000133"
                + "&searchWrd=&menuNo=205020&pageIndex=1&categoryCode=&nttId=72330");
        assertThat(first.publisherName()).isEqualTo(PUBLISHER);
        // 서울 2026-11-26 00:00
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-11-25T15:00:00Z"));
        assertThat(first.cveId()).isNull();
        assertThat(page.hasNextPage()).isTrue();
    }

    @Test
    void readsPostedDateNotSeverityCellsOnVulnerabilityBoard() {
        KisaBoardPage page = parseFixture("vulnerability-page-1", KisaBoard.VULNERABILITY, 1);

        assertThat(page.pinnedArticles()).isEmpty();
        assertThat(page.regularArticles()).hasSize(10).allSatisfy(article -> {
            assertThat(article.title()).startsWith("CVE-");
            assertThat(article.publishedAt()).isNotNull();
            assertThat(article.originalUrl()).contains("bbsId=B0000302").contains("menuNo=205023");
        });
        assertThat(page.regularArticles().getFirst().publishedAt()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
    }

    @Test
    void parsesReportBoardWithGradeColumn() {
        KisaBoardPage page = parseFixture("report-page-1", KisaBoard.REPORT, 1);

        // 고정 글(72145)은 일반 목록에도 있어 고정 쪽에만 한 번: 일반 글 9건
        assertThat(page.pinnedArticles()).extracting(CollectedArticle::title).containsExactly("사이버 위협 동향 보고서(2026년 상반기)");
        assertThat(page.regularArticles()).hasSize(9);
        assertThat(page.regularArticles().getFirst().title()).isEqualTo("AI 에이전트·업무 자동화 도구 보안수칙");
    }

    @Test
    void keepsPinnedCopyOnceAndSkipsLevelGuideTable() {
        KisaBoardPage page = parseFixture("alert-level-page-1", KisaBoard.ALERT_LEVEL, 1);

        // 고정 글과 첫 일반 글이 같은 글 (nttId 72233): 한 번만, 5단계 안내 표 행은 글이 아님
        List<CollectedArticle> allArticles = new java.util.ArrayList<>(page.pinnedArticles());
        allArticles.addAll(page.regularArticles());
        assertThat(allArticles).extracting(CollectedArticle::originalUrl).doesNotHaveDuplicates().hasSize(10);
        assertThat(allArticles.getFirst().title()).isEqualTo("사이버위기 경보 단계 '관심' → '주의' 상향 조정");
    }

    @Test
    void lastPageHasNoNextPage() {
        KisaBoardPage page = parseFixture("alert-level-page-7", KisaBoard.ALERT_LEVEL, 7);

        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.regularArticles().getLast().title()).contains("Revacc 변종 백도어");
        assertThat(page.regularArticles().getLast().publishedAt()).isEqualTo(Instant.parse("2004-07-05T15:00:00Z"));
    }

    @Test
    void pageAfterLastIsEmpty() {
        KisaBoardPage page = parseFixture("vulnerability-page-19-empty", KisaBoard.VULNERABILITY, 19);

        assertThat(page.regularArticles()).isEmpty();
        assertThat(page.pinnedArticles()).isEmpty();
        assertThat(page.hasNextPage()).isFalse();
    }

    static byte[] readFixture(String fixtureName) {
        try (InputStream stream = KisaBoardListParserTest.class.getResourceAsStream("/fixtures/kisa-board/" + fixtureName + ".html")) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static KisaBoardPage parseFixture(String fixtureName, KisaBoard board, int pageIndex) {
        return KisaBoardListParser.parse(readFixture(fixtureName), board, PUBLISHER, pageIndex);
    }
}
