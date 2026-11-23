package kr.suhsaechan.secbrief.advisory.support;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트용 Source 생성: 엔티티에 테스트 전용 공개 생성자를 만들지 않고 리플렉션으로 필드를 채움
 */
public final class SourceFixtures {

    // 수집 주기 (분): 시드 RSS 출처와 같은 값
    private static final int DEFAULT_INTERVAL_MINUTES = 30;
    // 실제 호출하지 않는 예약 도메인
    private static final String FEED_URL_PREFIX = "https://feed.example.test/";

    private SourceFixtures() {
    }

    /**
     * 켜진 출처 1건: 마지막 수집 시각이 있으면 마지막 상태는 성공
     */
    public static Source source(String code, ArticleKind kind, ArticleRegion region,
                                FeedFormat feedFormat, Instant lastCollectedAt) {
        Source source = BeanUtils.instantiateClass(Source.class);
        ReflectionTestUtils.setField(source, "code", code);
        ReflectionTestUtils.setField(source, "displayName", "표시명 " + code);
        ReflectionTestUtils.setField(source, "kind", kind);
        ReflectionTestUtils.setField(source, "region", region);
        ReflectionTestUtils.setField(source, "feedFormat", feedFormat);
        ReflectionTestUtils.setField(source, "feedUrl", FEED_URL_PREFIX + code);
        ReflectionTestUtils.setField(source, "intervalMinutes", DEFAULT_INTERVAL_MINUTES);
        ReflectionTestUtils.setField(source, "isEnabled", true);
        ReflectionTestUtils.setField(source, "lastCollectedAt", lastCollectedAt);
        if (lastCollectedAt != null) {
            ReflectionTestUtils.setField(source, "lastStatus", CollectStatus.SUCCESS);
        }
        return source;
    }
}
