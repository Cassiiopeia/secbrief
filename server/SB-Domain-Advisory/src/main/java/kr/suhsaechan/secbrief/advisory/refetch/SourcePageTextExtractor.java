package kr.suhsaechan.secbrief.advisory.refetch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 공개 원문 페이지(HTML) > 요약 입력 재료 글 (정리 전): 본문 칸 글, 본문 문단, 게시자 요약, Debian 공지 메일·보안 추적 표
 * 칸을 못 찾으면 빈 값 (호출부가 NOT_FOUND 로 셈), 인코딩은 응답의 meta 로 판단
 */
final class SourcePageTextExtractor {

    // KISA 게시글 본문 칸 (게시판 4곳 같은 틀), 없으면 게시글 틀의 본문 영역
    static final List<String> KISA_BODY_SELECTORS = List.of(".board .content_html", ".board .content");
    // 기사 본문 칸: BleepingComputer, Node.js 블로그 (출처 코드 순서와 무관, 먼저 찾은 칸)
    static final List<String> ARTICLE_BODY_SELECTORS = List.of(".articleBody", "main article", "main");
    // 본문 칸 안에서 글로 읽는 블록 (광고·관련 기사·그림 설명 칸을 빼려고 문단·목록·소제목만)
    private static final String ARTICLE_TEXT_BLOCKS = "p, li, h2, h3, h4";
    // 게시자 요약: 공유용 요약 > 검색용 요약
    private static final List<String> DESCRIPTION_SELECTORS =
            List.of("meta[property=og:description]", "meta[name=description]");
    // Debian 공지 메일 보관 페이지(MHonArc)의 본문 표시 주석, 서명 블록 시작
    private static final String MAIL_BODY_START_MARKER = "<!--X-Body-of-Message-->";
    private static final String MAIL_BODY_END_MARKER = "<!--X-Body-of-Message-End-->";
    private static final String PGP_SIGNATURE_START = "-----BEGIN PGP SIGNATURE-----";
    // Debian 보안 추적 페이지에서 글이 아닌 칸 (검색 칸, 바닥글)
    private static final String TRACKER_NON_CONTENT = "form, footer, script, style";
    private static final String BLOCK_SEPARATOR = "\n";

    private SourcePageTextExtractor() {
    }

    /** 본문 칸 전체 글 (표 포함): 먼저 찾은 칸 */
    static Optional<String> extractContainerText(byte[] pageHtml, String pageUrl, List<String> containerSelectors) {
        return findFirst(readDocument(pageHtml, pageUrl), containerSelectors).map(Element::text).filter(text -> !text.isBlank());
    }

    /** 본문 칸의 문단·목록·소제목 글 (안쪽 블록이 겹치면 바깥 블록만) */
    static Optional<String> extractArticleParagraphs(byte[] pageHtml, String pageUrl, List<String> containerSelectors) {
        return findFirst(readDocument(pageHtml, pageUrl), containerSelectors).map(SourcePageTextExtractor::toBlockText)
                .filter(text -> !text.isBlank());
    }

    /** 게시자 요약 (meta description) */
    static Optional<String> extractDescription(byte[] pageHtml, String pageUrl) {
        Document document = readDocument(pageHtml, pageUrl);
        return DESCRIPTION_SELECTORS.stream().map(document::selectFirst).filter(meta -> meta != null)
                .map(meta -> meta.attr("content")).filter(content -> !content.isBlank()).findFirst();
    }

    /** Debian 공지 메일 본문 (서명 블록 앞까지): 표시 주석 사이, 없으면 빈 값 */
    static Optional<String> extractDebianAnnouncementText(byte[] pageHtml, String pageUrl) {
        String html = readDocument(pageHtml, pageUrl).outerHtml();
        int bodyStart = html.indexOf(MAIL_BODY_START_MARKER);
        int bodyEnd = html.indexOf(MAIL_BODY_END_MARKER);
        if (bodyStart < 0 || bodyEnd <= bodyStart) {
            return Optional.empty();
        }
        String mailHtml = html.substring(bodyStart + MAIL_BODY_START_MARKER.length(), bodyEnd);
        String mailText = Jsoup.parseBodyFragment(mailHtml).text();
        int signatureStart = mailText.indexOf(PGP_SIGNATURE_START);
        String unsignedText = signatureStart < 0 ? mailText : mailText.substring(0, signatureStart);
        return Optional.of(unsignedText.strip()).filter(text -> !text.isBlank());
    }

    /** Debian 보안 추적 페이지 글 (권고 이름·설명·CVE·패키지별 고친 버전 표), 검색 칸·바닥글 제외 */
    static Optional<String> extractDebianTrackerText(byte[] pageHtml, String pageUrl) {
        Document document = readDocument(pageHtml, pageUrl);
        document.select(TRACKER_NON_CONTENT).remove();
        return Optional.of(document.body().text()).filter(text -> !text.isBlank());
    }

    private static Optional<Element> findFirst(Document document, List<String> selectors) {
        return selectors.stream().map(document::selectFirst).filter(element -> element != null).findFirst();
    }

    private static String toBlockText(Element container) {
        Elements blocks = container.select(ARTICLE_TEXT_BLOCKS);
        return blocks.stream()
                .filter(block -> block.parents().stream().noneMatch(blocks::contains))
                .map(Element::text).filter(text -> !text.isBlank())
                .collect(Collectors.joining(BLOCK_SEPARATOR));
    }

    // 형식 오류는 FEED_PARSE_ERROR
    private static Document readDocument(byte[] pageHtml, String pageUrl) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(pageHtml), null, pageUrl);
        } catch (IOException | RuntimeException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
    }
}
