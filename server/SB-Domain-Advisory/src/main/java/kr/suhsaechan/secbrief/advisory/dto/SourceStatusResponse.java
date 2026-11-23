package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.entity.Source;

/**
 * 출처 상태: 코드, 표시 이름, 종류·지역, 마지막 성공 수집 시각, 마지막 시도 상태
 */
public record SourceStatusResponse(
        String code,
        String displayName,
        ArticleKind kind,
        ArticleRegion region,
        Instant lastCollectedAt,
        CollectStatus lastStatus
) {

    public static SourceStatusResponse from(Source source) {
        return new SourceStatusResponse(source.getCode(), source.getDisplayName(), source.getKind(),
                source.getRegion(), source.getLastCollectedAt(), source.getLastStatus());
    }
}
