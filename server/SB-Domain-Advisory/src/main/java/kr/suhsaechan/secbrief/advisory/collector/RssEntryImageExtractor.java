package kr.suhsaechan.secbrief.advisory.collector;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jdom2.Element;

/**
 * 피드 항목 > 대표 이미지 주소: 첨부(enclosure) > Media RSS(thumbnail, content) > 요약 HTML 의 첫 img
 * 주소만 뽑고 요약 글은 돌려주지 않음, 주소 검증(http·길이)은 정규화 단계가 함
 */
final class RssEntryImageExtractor {

    private static final String IMAGE_TYPE_PREFIX = "image/";
    // Media RSS 네임스페이스 (끝 슬래시 없는 피드도 있음)
    private static final String MEDIA_NAMESPACE_URI_PREFIX = "http://search.yahoo.com/mrss";
    private static final String MEDIA_THUMBNAIL = "thumbnail";
    private static final String MEDIA_CONTENT = "content";
    private static final String MEDIA_GROUP = "group";
    private static final String MEDIA_MEDIUM_IMAGE = "image";
    // <img ... src="주소"> 또는 src='주소' (대소문자 무시)
    private static final Pattern IMAGE_TAG_SOURCE = Pattern.compile(
            "<img\\b[^>]*?\\bsrc\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // HTML 로 적힌 & (이름·10진·16진 표기: &amp; &#38; &#038; &#x26;)
    private static final Pattern ESCAPED_AMPERSAND = Pattern.compile("&(amp|#0*38|#x0*26);", Pattern.CASE_INSENSITIVE);

    private RssEntryImageExtractor() {
    }

    // 세 경로 모두 없으면 null
    static String extractImageSourceUrl(SyndEntry entry) {
        return findEnclosureImageUrl(entry.getEnclosures())
                .or(() -> findMediaImageUrl(entry.getForeignMarkup()))
                .or(() -> findFirstImageTagUrl(entry))
                .orElse(null);
    }

    // type 이 image/* 인 첫 첨부
    private static Optional<String> findEnclosureImageUrl(List<SyndEnclosure> enclosures) {
        return enclosures.stream()
                .filter(enclosure -> isImageType(enclosure.getType()))
                .map(SyndEnclosure::getUrl)
                .flatMap(url -> toNonBlank(url).stream())
                .findFirst();
    }

    // media:thumbnail 먼저 (media:content·media:group 안쪽 포함), 없으면 이미지인 media:content
    private static Optional<String> findMediaImageUrl(List<Element> foreignElements) {
        List<Element> mediaElements = foreignElements.stream()
                .filter(RssEntryImageExtractor::isMediaElement)
                .flatMap(element -> MEDIA_GROUP.equals(element.getName())
                        ? element.getChildren().stream().filter(RssEntryImageExtractor::isMediaElement)
                        : List.of(element).stream())
                .toList();
        return findMediaThumbnailUrl(mediaElements).or(() -> findMediaContentImageUrl(mediaElements));
    }

    private static Optional<String> findMediaThumbnailUrl(List<Element> mediaElements) {
        return mediaElements.stream()
                .flatMap(element -> MEDIA_THUMBNAIL.equals(element.getName())
                        ? List.of(element).stream()
                        : element.getChildren().stream().filter(RssEntryImageExtractor::isMediaThumbnail))
                .flatMap(thumbnail -> toNonBlank(thumbnail.getAttributeValue("url")).stream())
                .findFirst();
    }

    // medium="image" 또는 type="image/*", 둘 다 안 준 항목도 후보 (이미지가 아니면 내려받기 단계가 거름)
    private static Optional<String> findMediaContentImageUrl(List<Element> mediaElements) {
        return mediaElements.stream()
                .filter(element -> MEDIA_CONTENT.equals(element.getName()))
                .filter(RssEntryImageExtractor::isImageMediaContent)
                .flatMap(content -> toNonBlank(content.getAttributeValue("url")).stream())
                .findFirst();
    }

    private static boolean isImageMediaContent(Element mediaContent) {
        String medium = mediaContent.getAttributeValue("medium");
        String type = mediaContent.getAttributeValue("type");
        if (medium == null && type == null) {
            return true;
        }
        return MEDIA_MEDIUM_IMAGE.equalsIgnoreCase(medium) || isImageType(type);
    }

    // 요약 HTML 의 첫 img (주소 안에 HTML 로 적힌 & 는 & 로)
    private static Optional<String> findFirstImageTagUrl(SyndEntry entry) {
        if (entry.getDescription() == null || entry.getDescription().getValue() == null) {
            return Optional.empty();
        }
        Matcher imageTagMatcher = IMAGE_TAG_SOURCE.matcher(entry.getDescription().getValue());
        if (!imageTagMatcher.find()) {
            return Optional.empty();
        }
        return toNonBlank(ESCAPED_AMPERSAND.matcher(imageTagMatcher.group(2)).replaceAll("&"));
    }

    private static boolean isMediaElement(Element element) {
        return element.getNamespaceURI().startsWith(MEDIA_NAMESPACE_URI_PREFIX);
    }

    private static boolean isMediaThumbnail(Element element) {
        return isMediaElement(element) && MEDIA_THUMBNAIL.equals(element.getName());
    }

    private static boolean isImageType(String mimeType) {
        return mimeType != null && mimeType.strip().toLowerCase(Locale.ROOT).startsWith(IMAGE_TYPE_PREFIX);
    }

    private static Optional<String> toNonBlank(String text) {
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.strip());
    }
}
