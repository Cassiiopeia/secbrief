package kr.suhsaechan.secbrief.advisory.entity;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import org.junit.jupiter.api.Test;

/**
 * 출처 캐시 검증값: 교체, 출처가 더 안 주면 비움, 컬럼 길이를 넘으면 버림
 */
class SourceFeedValidatorsTest {

    private final Source source = createSource(SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);

    @Test
    void replacesBothValidators() {
        source.replaceFeedValidators("\"v1\"", "Wed, 25 Nov 2026 00:00:00 GMT");

        assertThat(source.getFeedEtag()).isEqualTo("\"v1\"");
        assertThat(source.getFeedLastModified()).isEqualTo("Wed, 25 Nov 2026 00:00:00 GMT");
    }

    @Test
    void clearsValidatorsWhenSourceStopsSendingThem() {
        source.replaceFeedValidators("\"v1\"", "Wed, 25 Nov 2026 00:00:00 GMT");

        source.replaceFeedValidators(null, null);

        assertThat(source.getFeedEtag()).isNull();
        assertThat(source.getFeedLastModified()).isNull();
    }

    @Test
    void dropsValidatorLongerThanColumn() {
        source.replaceFeedValidators("e".repeat(256), "m".repeat(65));

        assertThat(source.getFeedEtag()).isNull();
        assertThat(source.getFeedLastModified()).isNull();
    }

    @Test
    void keepsValidatorExactlyAtColumnLength() {
        source.replaceFeedValidators("e".repeat(255), "m".repeat(64));

        assertThat(source.getFeedEtag()).hasSize(255);
        assertThat(source.getFeedLastModified()).hasSize(64);
    }
}
