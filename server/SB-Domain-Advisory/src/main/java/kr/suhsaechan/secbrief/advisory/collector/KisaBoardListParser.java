package kr.suhsaechan.secbrief.advisory.collector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * KISA 게시판 목록 쪽 파싱: 행 > 수집 원본 (제목, 글 주소, 게시자, 게시일), 쪽 번호 목록 > 다음 쪽 여부
 * 게시판 4곳의 열 구성이 달라 열 순서가 아니라 표시(제목 칸 링크의 nttId, 날짜 모양 칸)로 읽음, 글 페이지는 읽지 않음
 */
public final class KisaBoardListParser {

    // 고정 글 행의 번호 칸 글자
    private static final String PINNED_ROW_NUMBER_TEXT = "공지";
    private static final Pattern NTT_ID = Pattern.compile("nttId=(\\d+)");
    private static final Pattern PAGE_INDEX = Pattern.compile("pageIndex=(\\d+)");
    // 게시일 칸: 2026-11-26 (취약점 정보 게시판은 심각도·CVSS 칸도 class="date" 라 모양으로 고름)
    private static final Pattern POSTED_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private KisaBoardListParser() {
    }

    /**
     * 목록 쪽 1개 파싱: 같은 글이 쪽 안에서 두 번 나오면 한 번만 (고정 글 = 첫 일반 글인 경우)
     * 게시일을 못 읽은 행은 버림 (수집 시각으로 바꾸면 오래된 글이 새 글로 보임)
     */
    public static KisaBoardPage parse(byte[] listPageHtml, KisaBoard board, String publisherName, int pageIndex) {
        Document document = readDocument(listPageHtml, board, pageIndex);
        List<CollectedArticle> pinnedArticles = new ArrayList<>();
        List<CollectedArticle> regularArticles = new ArrayList<>();
        Set<String> seenNttIds = new HashSet<>();
        for (Element row : document.select("tbody tr")) {
            Optional<Element> titleLink = Optional.ofNullable(row.selectFirst("td.sbj a[href*=nttId=]"));
            Optional<String> nttId = titleLink.flatMap(link -> findFirstGroup(NTT_ID, link.attr("href")));
            Optional<Instant> publishedAt = findPostedAt(row);
            if (nttId.isEmpty() || publishedAt.isEmpty() || !seenNttIds.add(nttId.get())) {
                continue;
            }
            CollectedArticle article = new CollectedArticle(titleLink.get().text(), board.toArticleUrl(nttId.get()),
                    publisherName, publishedAt.get(), null, null, null, false);
            (isPinnedRow(row) ? pinnedArticles : regularArticles).add(article);
        }
        return new KisaBoardPage(pinnedArticles, regularArticles, hasPageAfter(document, pageIndex));
    }

    // 인코딩은 응답의 meta 로 판단 (형식 오류는 FEED_PARSE_ERROR)
    private static Document readDocument(byte[] listPageHtml, KisaBoard board, int pageIndex) {
        try {
            return Jsoup.parse(new ByteArrayInputStream(listPageHtml), null, board.toListPageUrl(pageIndex));
        } catch (IOException | RuntimeException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
    }

    private static boolean isPinnedRow(Element row) {
        Element numberCell = row.selectFirst("td.num");
        return numberCell != null && PINNED_ROW_NUMBER_TEXT.equals(numberCell.text().strip());
    }

    // 날짜 모양 칸 중 마지막 > 서울 그날 00:00 (RSS 의 시각 없는 게시일과 같은 해석)
    private static Optional<Instant> findPostedAt(Element row) {
        List<String> dateTexts = row.select("td.date").stream()
                .map(cell -> cell.text().strip())
                .filter(text -> POSTED_DATE.matcher(text).matches())
                .toList();
        if (dateTexts.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(dateTexts.getLast()).atStartOfDay(SeoulCalendar.SEOUL).toInstant());
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    // 쪽 번호 링크 중 가장 큰 번호 > 지금 쪽 (마지막 쪽 다음은 빈 표 + 앞쪽 번호만 있음)
    private static boolean hasPageAfter(Document document, int pageIndex) {
        return document.select(".pagination a[href*=pageIndex=]").stream()
                .map(link -> findFirstGroup(PAGE_INDEX, link.attr("href")))
                .flatMap(Optional::stream)
                .mapToInt(Integer::parseInt)
                .anyMatch(linkedPageIndex -> linkedPageIndex > pageIndex);
    }

    private static Optional<String> findFirstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
