package kr.suhsaechan.secbrief.advisory.collector;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;

/**
 * KISA 보호나라 게시판 4곳: 출처 코드, 게시판 번호(bbsId), 메뉴 번호(menuNo), 지난 글 채우기 범위
 * 게시판 번호는 출처 피드 주소(rss.do?bbsId=...)와 같은 값 (V0.3.0, V0.5.0 시드)
 */
public enum KisaBoard {
    // 채우기 범위 null: 게시판 전체 (경보단계는 69건뿐이고 변경 이력 전체가 필요)
    NOTICE(SourceCode.KISA, "B0000133", "205020", Duration.ofDays(365)),
    VULNERABILITY(SourceCode.KISA_VULNERABILITY, "B0000302", "205023", Duration.ofDays(365)),
    REPORT(SourceCode.KISA_REPORT, "B0000127", "205021", Duration.ofDays(365)),
    ALERT_LEVEL(SourceCode.KISA_ALERT_LEVEL, "B0000342", "205024", null);

    private static final String SITE_ORIGIN = "https://www.boho.or.kr";
    // 목록 쪽: /kr/bbs/list.do?menuNo={menuNo}&bbsId={bbsId}&pageIndex={쪽}
    private static final String LIST_PAGE_URL_FORMAT = SITE_ORIGIN + "/kr/bbs/list.do?menuNo=%s&bbsId=%s&pageIndex=%d";
    private static final String BOARD_URL_FORMAT = SITE_ORIGIN + "/kr/bbs/list.do?menuNo=%s&bbsId=%s";
    // 글 주소: RSS 피드 link 와 글자 하나까지 같은 모양 (원문 링크로 중복을 거르므로 쪽 번호는 항상 1)
    private static final String ARTICLE_URL_FORMAT = SITE_ORIGIN
            + "/kr/bbs/view.do?searchCnd=&bbsId=%s&searchWrd=&menuNo=%s&pageIndex=1&categoryCode=&nttId=%s";

    private final String sourceCode;
    private final String bbsId;
    private final String menuNo;
    private final Duration backfillWindow;

    KisaBoard(String sourceCode, String bbsId, String menuNo, Duration backfillWindow) {
        this.sourceCode = sourceCode;
        this.bbsId = bbsId;
        this.menuNo = menuNo;
        this.backfillWindow = backfillWindow;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    /** 지난 글 채우기 범위 (비어 있으면 게시판 전체) */
    public Optional<Duration> getBackfillWindow() {
        return Optional.ofNullable(backfillWindow);
    }

    public String toListPageUrl(int pageIndex) {
        return LIST_PAGE_URL_FORMAT.formatted(menuNo, bbsId, pageIndex);
    }

    /** 게시판 첫 화면 (경보단계는 5단계 안내 표가 함께 있음) */
    public String toBoardUrl() {
        return BOARD_URL_FORMAT.formatted(menuNo, bbsId);
    }

    public String toArticleUrl(String nttId) {
        return ARTICLE_URL_FORMAT.formatted(bbsId, menuNo, nttId);
    }

    public static Optional<KisaBoard> fromSourceCode(String sourceCode) {
        return Arrays.stream(values()).filter(board -> board.sourceCode.equals(sourceCode)).findFirst();
    }
}
