package kr.suhsaechan.secbrief.advisory.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactPolicy;
import kr.suhsaechan.secbrief.common.exception.ExceptionLogText;
import kr.suhsaechan.secbrief.common.text.TextTruncation;
import org.springframework.web.util.HtmlUtils;

/**
 * 수집 항목 정규화: 제목·링크 정리, 게시일 보정, 출처 값·긴급 여부 적용, 요약 입력·사실 값 정리 > 저장용 초안
 * 제목이 비거나 링크가 http(s) 가 아니면 버림 (빈 값), 이미지 주소만 불량이면 이미지 없이 저장
 */
public final class ArticleNormalizer {

    // 게시일이 수집 시각보다 이만큼까지 미래인 것은 시계 오차로 보고 그대로 둠
    public static final Duration FUTURE_PUBLISHED_AT_TOLERANCE = Duration.ofHours(1);
    // article.title 열 길이
    public static final int MAXIMUM_TITLE_LENGTH = 500;
    // article.original_url 열 길이 (넘으면 잘라 쓰면 링크가 깨지므로 버림)
    private static final int MAXIMUM_ORIGINAL_URL_LENGTH = 1000;
    // article.image_source_url 열 길이 (넘으면 이미지 없음으로 처리)
    private static final int MAXIMUM_IMAGE_SOURCE_URL_LENGTH = 1000;
    // article.publisher_name 열 길이
    private static final int MAXIMUM_PUBLISHER_NAME_LENGTH = 100;

    private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
    // 일반 공백 + 줄바꿈 + 줄바꿈 없는 공백(NBSP)
    private static final Pattern WHITESPACE_RUN = Pattern.compile("[\\s\\u00A0]+");
    // 유니코드 제어문자 (줄바꿈·NUL 등)
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cc}");
    private static final String URL_FRAGMENT_START = "#";
    private static final String HTTP_URL_PREFIX = "http://";
    private static final String HTTPS_URL_PREFIX = "https://";

    private ArticleNormalizer() {
    }

    /**
     * 수집 항목 > 저장용 초안: 제목·링크 정리 > 게시일 보정 > 출처 값(종류·지역·게시자) > 긴급 판정 > 이미지 주소 정리
     * > 요약 입력 정리(SourceTextPolicy) > 사실 값 정리(SourceFactPolicy)
     */
    public static Optional<ArticleDraft> normalize(CollectedArticle collected, Source source, Instant collectedAt) {
        // 제목·링크 정리 (불량이면 버림)
        String title = toCleanedTitle(collected.title());
        String originalUrl = toCleanedUrl(collected.originalUrl());
        if (title.isEmpty() || !isHttpUrlWithin(originalUrl, MAXIMUM_ORIGINAL_URL_LENGTH)) {
            return Optional.empty();
        }

        // 게시일 보정 (없음·미래 > 수집 시각 + 추정 표시)
        boolean isPublishedAtEstimated = isPublishedAtUnreliable(collected.publishedAt(), collectedAt);
        Instant publishedAt = isPublishedAtEstimated ? collectedAt : collected.publishedAt();

        boolean isUrgent = ArticleUrgencyPolicy.isUrgentAt(
                source.getCode(), title, collected.cvssScore(), collected.isKev(), publishedAt, collectedAt);

        return Optional.of(new ArticleDraft(
                source.getKind(),
                source.getRegion(),
                title,
                originalUrl,
                toPublisherName(collected.publisherName(), source),
                publishedAt,
                isPublishedAtEstimated,
                collected.cveId(),
                collected.cvssScore(),
                collected.cvssVersion(),
                collected.isKev(),
                isUrgent,
                toImageSourceUrl(collected.imageSourceUrl()),
                SourceTextPolicy.toSourceText(collected.sourceText(), title),
                SourceFactPolicy.toStorableFacts(collected.sourceFacts())));
    }

    /**
     * 오류 메시지 정리 (제어문자 > ?, 500자 제한, null > unknown)
     */
    public static String toSanitizedErrorMessage(String rawMessage) {
        return ExceptionLogText.toSanitizedErrorMessage(rawMessage);
    }

    // HTML 태그 제거 > 문자 참조 풀기(&#39; &amp; 등, KISA 피드가 제목을 이렇게 줌) > 연속 공백 하나로 > trim > 500자 제한
    private static String toCleanedTitle(String rawTitle) {
        if (rawTitle == null) {
            return "";
        }
        String withoutTags = HTML_TAG.matcher(rawTitle).replaceAll("");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        String collapsed = WHITESPACE_RUN.matcher(unescaped).replaceAll(" ").trim();
        return TextTruncation.truncateKeepingSurrogatePairs(collapsed, MAXIMUM_TITLE_LENGTH).trim();
    }

    // trim > #fragment 제거
    private static String toCleanedUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String trimmed = rawUrl.trim();
        int fragmentIndex = trimmed.indexOf(URL_FRAGMENT_START);
        return fragmentIndex >= 0 ? trimmed.substring(0, fragmentIndex) : trimmed;
    }

    // trim > http(s) 가 아니거나 열 길이를 넘거나 공백·제어문자가 섞였으면 null (이미지 없음)
    private static String toImageSourceUrl(String rawImageUrl) {
        if (rawImageUrl == null) {
            return null;
        }
        String trimmed = rawImageUrl.trim();
        boolean isUsable = isHttpUrlWithin(trimmed, MAXIMUM_IMAGE_SOURCE_URL_LENGTH)
                && !WHITESPACE_RUN.matcher(trimmed).find()
                && !CONTROL_CHARACTER.matcher(trimmed).find();
        return isUsable ? trimmed : null;
    }

    // http(s) 스킴 + 호스트 있음 + 열 길이 이내
    private static boolean isHttpUrlWithin(String url, int maximumLength) {
        return url.length() <= maximumLength
                && (hasHostAfterPrefix(url, HTTPS_URL_PREFIX) || hasHostAfterPrefix(url, HTTP_URL_PREFIX));
    }

    private static boolean hasHostAfterPrefix(String url, String prefix) {
        return url.regionMatches(true, 0, prefix, 0, prefix.length()) && url.length() > prefix.length();
    }

    // 게시일 없음 또는 수집 시각 + 허용 오차보다 미래
    private static boolean isPublishedAtUnreliable(Instant publishedAt, Instant collectedAt) {
        return publishedAt == null || publishedAt.isAfter(collectedAt.plus(FUTURE_PUBLISHED_AT_TOLERANCE));
    }

    // 수집값이 비면 출처 표시 이름
    private static String toPublisherName(String collectedPublisherName, Source source) {
        String publisherName = collectedPublisherName == null || collectedPublisherName.isBlank()
                ? source.getDisplayName()
                : collectedPublisherName.trim();
        return TextTruncation.truncateKeepingSurrogatePairs(publisherName, MAXIMUM_PUBLISHER_NAME_LENGTH);
    }
}
