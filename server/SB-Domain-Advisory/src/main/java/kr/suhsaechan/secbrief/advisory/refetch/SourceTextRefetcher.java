package kr.suhsaechan.secbrief.advisory.refetch;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.collector.NvdRequestThrottle;
import kr.suhsaechan.secbrief.advisory.policy.SourceTextPolicy;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 요약 입력 다시 받기 (기사 1건): 출처별 공개 원문 위치(SourceTextRefetchMethod) 요청 > 글 꺼내기 > 운영과 같은 정리(SourceTextPolicy)
 * 받은 글은 요약 입력으로만 씀 (요약이 끝나면 지움), 요청 사이 2초 쉼 (NVD 는 6초), 실패는 예외 대신 NOT_FOUND + 사유
 */
@Slf4j
@Component
public class SourceTextRefetcher {

    // 페이지 요청 간격 (출처 부담, KISA 게시판 넘김과 같은 2초)
    static final Duration PAGE_REQUEST_INTERVAL = Duration.ofSeconds(2);
    // NVD CVE 1건 조회: https://services.nvd.nist.gov/rest/json/cves/2.0?cveId={CVE ID}
    static final String NVD_CVE_API_PREFIX = "https://services.nvd.nist.gov/rest/json/cves/2.0?cveId=";
    // Red Hat CSAF: https://security.access.redhat.com/data/csaf/v2/advisories/{연도}/rhsa-{연도}_{번호}.json
    static final String REDHAT_CSAF_URL_FORMAT = "https://security.access.redhat.com/data/csaf/v2/advisories/%s/rhsa-%s_%s.json";
    // Debian 보안 추적 페이지: https://security-tracker.debian.org/tracker/{DSA 번호}
    static final String DEBIAN_TRACKER_URL_PREFIX = "https://security-tracker.debian.org/tracker/";
    // Ubuntu 권고 JSON: 권고 주소 + .json
    private static final String UBUNTU_NOTICE_JSON_SUFFIX = ".json";
    // 원문 주소의 Red Hat 권고 번호 (RHSA-2026:102399 > 연도 2026, 번호 102399)
    private static final Pattern REDHAT_ADVISORY_ID = Pattern.compile("RHSA-(\\d{4}):(\\d+)");
    // 제목의 Debian 권고 번호 (DSA-6663-1 thunderbird - security update)
    private static final Pattern DEBIAN_ADVISORY_ID = Pattern.compile("DSA-\\d+-\\d+");

    private final FeedHttpClient feedHttpClient;
    private final Clock clock;
    private final Duration pageRequestInterval;
    private final NvdRequestThrottle nvdRequestThrottle;
    // 직전 페이지 요청 시각 (요청한 적 없으면 null)
    private Instant lastPageRequestedAt;

    @Autowired
    public SourceTextRefetcher(FeedHttpClient feedHttpClient, Clock clock) {
        this(feedHttpClient, clock, PAGE_REQUEST_INTERVAL, new NvdRequestThrottle(clock, Thread::sleep));
    }

    // 쉼 없이 확인하는 테스트용
    SourceTextRefetcher(FeedHttpClient feedHttpClient, Clock clock, Duration pageRequestInterval,
                        NvdRequestThrottle nvdRequestThrottle) {
        this.feedHttpClient = feedHttpClient;
        this.clock = clock;
        this.pageRequestInterval = pageRequestInterval;
        this.nvdRequestThrottle = nvdRequestThrottle;
    }

    /**
     * 기사 1건: 다시 받는 곳 판정 > 요청·글 꺼내기 > 정리 > 요약할 내용이 있으면 RECOVERED, 없으면 TOO_THIN
     */
    public synchronized SourceTextRefetchResult refetch(SourceTextRefetchTarget target) {
        SourceTextRefetchMethod method = SourceTextRefetchMethod.of(target.sourceCode());
        if (method == SourceTextRefetchMethod.NOT_SUPPORTED) {
            return SourceTextRefetchResult.notSupported();
        }
        if (!hasRequiredIdentifier(method, target)) {
            return SourceTextRefetchResult.notFound("identifier missing");
        }
        try {
            Optional<String> rawText = fetchRawText(method, target);
            if (rawText.isEmpty()) {
                return SourceTextRefetchResult.notFound("content not found");
            }
            return toResult(rawText.get(), target.title());
        } catch (CustomException exception) {
            // 응답 오류·형식 오류: 사유만 남김 (주소·본문은 남기지 않음)
            return SourceTextRefetchResult.notFound(exception.getMessage());
        }
    }

    // 요청 주소를 만들 식별자 (NVD CVE ID, Red Hat 원문 주소의 권고 번호, Debian 제목의 권고 번호)
    private static boolean hasRequiredIdentifier(SourceTextRefetchMethod method, SourceTextRefetchTarget target) {
        return switch (method) {
            case NVD_CVE_API -> target.cveId() != null;
            case REDHAT_CSAF_JSON -> REDHAT_ADVISORY_ID.matcher(target.originalUrl()).find();
            case DEBIAN_ANNOUNCEMENT_PAGE -> DEBIAN_ADVISORY_ID.matcher(target.title()).find();
            default -> true;
        };
    }

    private Optional<String> fetchRawText(SourceTextRefetchMethod method, SourceTextRefetchTarget target) {
        String originalUrl = target.originalUrl();
        return switch (method) {
            case NVD_CVE_API -> fetchNvdDescription(target);
            case UBUNTU_NOTICE_JSON ->
                    SourceJsonTextExtractor.extractUbuntuNoticeText(fetchPage(originalUrl + UBUNTU_NOTICE_JSON_SUFFIX));
            case REDHAT_CSAF_JSON -> SourceJsonTextExtractor.extractRedHatCsafText(fetchPage(toRedHatCsafUrl(originalUrl)));
            case DEBIAN_ANNOUNCEMENT_PAGE -> fetchDebianAdvisoryText(target);
            case KISA_BOARD_VIEW_PAGE -> SourcePageTextExtractor.extractContainerTextWithTableRows(
                    fetchPage(originalUrl), originalUrl, SourcePageTextExtractor.KISA_BODY_SELECTORS);
            case ARTICLE_PAGE_BODY -> SourcePageTextExtractor.extractArticleParagraphs(
                    fetchPage(originalUrl), originalUrl, SourcePageTextExtractor.ARTICLE_BODY_SELECTORS);
            case ARTICLE_PAGE_DESCRIPTION -> SourcePageTextExtractor.extractDescription(fetchPage(originalUrl), originalUrl);
            case NOT_SUPPORTED -> Optional.empty();
        };
    }

    // CVE ID 1건 조회 (NVD 요청 간격 6초)
    private Optional<String> fetchNvdDescription(SourceTextRefetchTarget target) {
        nvdRequestThrottle.awaitTurn();
        return SourceJsonTextExtractor.extractNvdDescription(
                feedHttpClient.fetch(NVD_CVE_API_PREFIX + target.cveId()), target.cveId());
    }

    // 공지 메일 보관 페이지 > 실패·본문 없음이면 보안 추적 페이지 (메일 보관 서버 503 이 잦음, #77)
    private Optional<String> fetchDebianAdvisoryText(SourceTextRefetchTarget target) {
        try {
            Optional<String> announcementText = SourcePageTextExtractor.extractDebianAnnouncementText(
                    fetchPage(target.originalUrl()), target.originalUrl());
            if (announcementText.isPresent()) {
                return announcementText;
            }
        } catch (CustomException exception) {
            log.info("[source text refetch] debian announcement unavailable, trying tracker : cause={}", exception.getMessage());
        }
        String trackerUrl = DEBIAN_TRACKER_URL_PREFIX + toDebianAdvisoryId(target.title());
        return SourcePageTextExtractor.extractDebianTrackerText(fetchPage(trackerUrl), trackerUrl);
    }

    private static String toRedHatCsafUrl(String originalUrl) {
        Matcher advisoryId = REDHAT_ADVISORY_ID.matcher(originalUrl);
        advisoryId.find();
        return REDHAT_CSAF_URL_FORMAT.formatted(advisoryId.group(1), advisoryId.group(1), advisoryId.group(2));
    }

    private static String toDebianAdvisoryId(String title) {
        Matcher advisoryId = DEBIAN_ADVISORY_ID.matcher(title);
        advisoryId.find();
        return advisoryId.group();
    }

    // 운영 정리 > 요약할 내용이 있는지 (제목 기준)
    private static SourceTextRefetchResult toResult(String rawText, String title) {
        String sourceText = SourceTextPolicy.toSourceText(rawText, title);
        if (sourceText != null) {
            return SourceTextRefetchResult.recovered(sourceText);
        }
        return SourceTextRefetchResult.tooThin(SourceTextPolicy.toCleanedText(rawText));
    }

    // 요청 차례 대기 (직전 요청 후 간격이 안 지났으면 남은 시간만큼) > 요청
    private byte[] fetchPage(String pageUrl) {
        Instant now = clock.instant();
        if (lastPageRequestedAt != null) {
            Duration remaining = Duration.between(now, lastPageRequestedAt.plus(pageRequestInterval));
            if (remaining.isPositive()) {
                sleepFor(remaining);
            }
        }
        lastPageRequestedAt = clock.instant();
        return feedHttpClient.fetch(pageUrl);
    }

    private static void sleepFor(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "interrupted while waiting page request interval");
        }
    }
}
