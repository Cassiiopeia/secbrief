package kr.suhsaechan.secbrief.advisory.collector;

import com.rometools.rome.feed.WireFeed;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.WireFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.Filters;

/**
 * RSS·Atom 피드 파싱: 항목 > 수집 원본 (제목, 링크, 게시자, 게시일, 대표 이미지 주소, 요약 입력)
 * 인코딩은 XmlReader 가 자동 감지, 요약 입력은 피드가 준 글만 (기사 페이지는 읽지 않음, 사실 값 없음)
 */
public final class RssFeedParser {

    // 시간대 없는 날짜: 2026-11-23, 2026-11-23 14:12:17 (시각 없으면 자정)
    private static final DateTimeFormatter ZONELESS_DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd[ HH:mm:ss]")
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .toFormatter(Locale.US);

    private RssFeedParser() {
    }

    /**
     * publisherName: 피드의 author 는 기자 이름이라 쓰지 않고 호출부가 준 게시자를 모든 항목에 적용
     */
    public static List<CollectedArticle> parse(InputStream feedStream, String publisherName) {
        SyndFeed feed = readFeed(feedStream);
        return feed.getEntries().stream()
                .map(entry -> toCollectedArticle(entry, publisherName))
                .toList();
    }

    // 인코딩 자동 감지 > 피드 읽기 (형식 오류는 FEED_PARSE_ERROR)
    private static SyndFeed readFeed(InputStream feedStream) {
        try (XmlReader reader = new XmlReader(feedStream)) {
            return new SyndFeedImpl(new SeoulZonelessDateFeedInput().build(reader), false);
        } catch (IOException | FeedException | IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.FEED_PARSE_ERROR, exception);
        }
    }

    private static CollectedArticle toCollectedArticle(SyndEntry entry, String publisherName) {
        return new CollectedArticle(
                stripOrNull(entry.getTitle()),
                stripOrNull(entry.getLink()),
                publisherName,
                toPublishedAt(entry),
                null,
                null,
                null,
                false,
                RssEntryImageExtractor.extractImageSourceUrl(entry),
                toSourceText(entry),
                List.of());
    }

    // 요약 입력: 요약문(description·summary) 우선, 없으면 본문 칸(content) — HTML 은 정규화 단계가 벗김
    private static String toSourceText(SyndEntry entry) {
        SyndContent description = entry.getDescription();
        if (description != null && description.getValue() != null && !description.getValue().isBlank()) {
            return description.getValue();
        }
        return entry.getContents().stream()
                .map(SyndContent::getValue)
                .filter(contentValue -> contentValue != null && !contentValue.isBlank())
                .findFirst()
                .orElse(null);
    }

    // 게시일 우선, 없으면 수정일, 둘 다 없으면 null
    private static Instant toPublishedAt(SyndEntry entry) {
        Date publishedDate = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        return publishedDate == null ? null : publishedDate.toInstant();
    }

    // CDATA 앞뒤 공백 제거 (KISA 피드가 " 제목 " 형태로 줌)
    private static String stripOrNull(String text) {
        return text == null ? null : text.strip();
    }

    /**
     * 시간대 없는 pubDate > 서울 시각 RFC 822 로 고쳐 Rome 이 읽게 함
     * KISA(2026-11-23)·데일리시큐(2026-11-23 14:12:17)가 이 형식이라 Rome 이 날짜를 못 읽음 (국내 출처 전제로 서울 시각)
     */
    private static final class SeoulZonelessDateFeedInput extends WireFeedInput {

        @Override
        public WireFeed build(Document document) throws IllegalArgumentException, FeedException {
            // 목록을 먼저 모은 뒤 수정 (순회 중 수정 방지)
            List<Element> pubDateElements = new ArrayList<>();
            document.getDescendants(Filters.element("pubDate")).forEach(pubDateElements::add);
            pubDateElements.forEach(pubDate -> toSeoulRfc822(pubDate.getTextTrim()).ifPresent(pubDate::setText));
            return super.build(document);
        }

        // RFC 822 등 다른 형식이면 비어 있음 (그대로 둠)
        private static Optional<String> toSeoulRfc822(String pubDateText) {
            try {
                LocalDateTime seoulLocalTime = LocalDateTime.parse(pubDateText, ZONELESS_DATE_FORMAT);
                return Optional.of(DateTimeFormatter.RFC_1123_DATE_TIME.format(seoulLocalTime.atZone(SeoulCalendar.SEOUL)));
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
    }
}
