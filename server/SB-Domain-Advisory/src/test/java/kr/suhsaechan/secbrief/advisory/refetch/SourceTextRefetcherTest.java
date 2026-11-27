package kr.suhsaechan.secbrief.advisory.refetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.advisory.collector.NvdRequestThrottle;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 요약 입력 다시 받기 (출처별 시험 자료, 외부 호출 없음): 요청 주소, 꺼내는 칸, 빼는 칸, 내용 없는 입력, 실패 사유
 * 시험 자료는 각 출처 페이지의 틀만 본뜬 가짜 글 (fixtures/refetch)
 */
class SourceTextRefetcherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC);
    private static final String KISA_URL = "https://www.boho.or.kr/kr/bbs/view.do?bbsId=B0000133&menuNo=205020&nttId=1";
    private static final String BLEEPING_URL = "https://www.bleepingcomputer.com/news/security/example/";
    private static final String NODEJS_URL = "https://nodejs.org/en/blog/vulnerability/example-security-releases";
    private static final String DAILYSECU_URL = "https://www.dailysecu.com/news/articleView.html?idxno=1";
    private static final String DEBIAN_URL = "https://lists.debian.org/debian-security-announce/2026/msg00585.html";
    private static final String DEBIAN_TITLE = "DSA-6663-1 example - security update";
    private static final String UBUNTU_URL = "https://ubuntu.com/security/notices/USN-9201-1";
    private static final String REDHAT_URL = "https://access.redhat.com/errata/RHSA-2026:102399";

    private final FeedHttpClient feedHttpClient = mock(FeedHttpClient.class);
    private final SourceTextRefetcher refetcher = new SourceTextRefetcher(feedHttpClient, CLOCK, Duration.ZERO,
            new NvdRequestThrottle(CLOCK, duration -> { }));

    @Test
    void readsKisaBoardViewBodyIncludingVersionTable() {
        when(feedHttpClient.fetch(KISA_URL)).thenReturn(readFixture("kisa-view.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.KISA, "Example 제품 보안 업데이트 권고", KISA_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).contains("Example Docs 제품에서 발생하는 취약점", "CVE-2026-0001 Example Docs 10.0.0 미만 10.0.0")
                .doesNotContain("알림마당", "공유", "Copyright");
    }

    @Test
    void readsArticleParagraphsOnlyFromBleepingComputerBody() {
        when(feedHttpClient.fetch(BLEEPING_URL)).thenReturn(readFixture("bleeping-article.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.BLEEPING_COMPUTER, "Hackers exploit Example Router cloud flaw",
                BLEEPING_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).startsWith("Example Corp confirmed that attackers exploited")
                .contains("Patch available", "Disable remote cloud access.")
                .doesNotContain("Menu paragraph", "Related story", "advertisement");
        // 목록 안 문단은 한 번만 (바깥 블록만 읽음)
        assertThat(refetchResult.sourceText().split("Update firmware to version 4.8.2", -1)).hasSize(2);
    }

    @Test
    void readsNodejsBlogMainWithoutBanner() {
        when(feedHttpClient.fetch(NODEJS_URL)).thenReturn(readFixture("nodejs-blog.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.NODEJS_SECURITY, "Example Security Releases", NODEJS_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).contains("Updates are now available for the 24.x", "HTTP/2 remote abort (CVE-2026-0002)")
                .doesNotContain("Commercial support banner");
    }

    @Test
    void readsOnlyPublisherDescriptionFromDailysecuPage() {
        when(feedHttpClient.fetch(DAILYSECU_URL)).thenReturn(readFixture("dailysecu-article.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.DAILYSECU, "예시 기업 개인정보 유출 조사", DAILYSECU_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).startsWith("예시 기업이 해커에게 협박 메일을 받았고").doesNotContain("본문 첫 문단");
    }

    @Test
    void readsDebianAnnouncementMailWithoutSignature() {
        when(feedHttpClient.fetch(DEBIAN_URL)).thenReturn(readFixture("debian-announcement.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.DEBIAN_DSA, DEBIAN_TITLE, DEBIAN_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).contains("Package : example", "fixed in version 1:2.0.0-1~deb13u1",
                "We recommend that you upgrade").doesNotContain("BEGIN PGP SIGNATURE", "iQIzBAEBCgAdFiEEexample", "Prev by Date");
        verify(feedHttpClient, never()).fetch(SourceTextRefetcher.DEBIAN_TRACKER_URL_PREFIX + "DSA-6663-1");
    }

    @Test
    void fallsBackToDebianTrackerWhenAnnouncementArchiveFails() {
        when(feedHttpClient.fetch(DEBIAN_URL)).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        when(feedHttpClient.fetch(SourceTextRefetcher.DEBIAN_TRACKER_URL_PREFIX + "DSA-6663-1"))
                .thenReturn(readFixture("debian-tracker.html"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.DEBIAN_DSA, DEBIAN_TITLE, DEBIAN_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).contains("example - security update", "CVE-2026-0003, CVE-2026-0004",
                "trixie (security) 1:2.0.0-1~deb13u1 fixed").doesNotContain("Search for package", "Home");
    }

    @Test
    void readsUbuntuNoticeJsonSummaryAndDescription() {
        when(feedHttpClient.fetch(UBUNTU_URL + ".json")).thenReturn(readFixture("ubuntu-notice.json"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.UBUNTU_USN, "USN-9201-1: Example vulnerability", UBUNTU_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).startsWith("Example could allow unintended changes")
                .contains("It was discovered that Example incorrectly granted a service user unrestricted access");
    }

    @Test
    void readsRedHatCsafTopicAndDetailsWithoutLegalNotice() {
        String csafUrl = "https://security.access.redhat.com/data/csaf/v2/advisories/2026/rhsa-2026_102399.json";
        when(feedHttpClient.fetch(csafUrl)).thenReturn(readFixture("redhat-csaf.json"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.REDHAT_RHSA, "RHSA-2026:102399: Important: example security update",
                REDHAT_URL, null);

        assertThat(refetchResult.isRecovered()).isTrue();
        assertThat(refetchResult.sourceText()).startsWith("An update for example is now available")
                .contains("Denial of Service via file descriptor exhaustion (CVE-2026-0005)")
                .doesNotContain("Creative Commons");
    }

    @Test
    void readsNvdDescriptionByCveIdAndMarksTooThinWhenTitleHasWholeDescription() {
        String description = "In Example Server before 2026.1.3 remote code execution was possible via build attribute injection";
        when(feedHttpClient.fetch(SourceTextRefetcher.NVD_CVE_API_PREFIX + "CVE-2026-0006")).thenReturn(readFixture("nvd-cve.json"));

        SourceTextRefetchResult recovered = refetch(SourceCode.NVD, "CVE-2026-0006: Example Server flaw", null, "CVE-2026-0006");
        SourceTextRefetchResult tooThin = refetch(SourceCode.NVD, "CVE-2026-0006: " + description, null, "CVE-2026-0006");

        assertThat(recovered.isRecovered()).isTrue();
        assertThat(recovered.sourceText()).isEqualTo(description);
        assertThat(tooThin.status()).isEqualTo(SourceTextRefetchResult.Status.TOO_THIN);
        assertThat(tooThin.sourceText()).isEqualTo(description);
    }

    @Test
    void skipsSourcesThatAreNotRefetched() {
        SourceTextRefetchResult refetchResult = refetch(SourceCode.THE_HACKER_NEWS, "Example", "https://thehackernews.com/a.html", null);

        assertThat(refetchResult.status()).isEqualTo(SourceTextRefetchResult.Status.NOT_SUPPORTED);
        verify(feedHttpClient, never()).fetch(anyString());
    }

    @Test
    void reportsFailureReasonWithoutThrowing() {
        when(feedHttpClient.fetch(BLEEPING_URL)).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=403"));

        SourceTextRefetchResult refetchResult = refetch(SourceCode.BLEEPING_COMPUTER, "Example", BLEEPING_URL, null);

        assertThat(refetchResult.status()).isEqualTo(SourceTextRefetchResult.Status.NOT_FOUND);
        assertThat(refetchResult.failureReason()).contains("status=403");
    }

    @Test
    void doesNotRequestWhenAdvisoryIdentifierIsMissing() {
        SourceTextRefetchResult redHatResult =
                refetch(SourceCode.REDHAT_RHSA, "Example", "https://access.redhat.com/errata/unknown", null);
        SourceTextRefetchResult nvdResult = refetch(SourceCode.NVD, "Example", null, null);

        assertThat(redHatResult.failureReason()).isEqualTo("identifier missing");
        assertThat(nvdResult.failureReason()).isEqualTo("identifier missing");
        verify(feedHttpClient, never()).fetch(anyString());
    }

    private SourceTextRefetchResult refetch(String sourceCode, String title, String originalUrl, String cveId) {
        return refetcher.refetch(new SourceTextRefetchTarget(sourceCode, title, originalUrl, cveId));
    }

    private static byte[] readFixture(String fileName) {
        try (InputStream stream = SourceTextRefetcherTest.class.getResourceAsStream("/fixtures/refetch/" + fileName)) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
