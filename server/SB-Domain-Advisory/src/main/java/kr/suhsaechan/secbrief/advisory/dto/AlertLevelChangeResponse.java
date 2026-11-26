package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.AlertLevelChangeDirection;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;

/**
 * 경보단계 변경 1건: 바뀌기 전 단계(제목에 없으면 한 건 앞 글의 단계, 모르면 null), 바뀐 단계(모르면 null), 방향, 원문
 */
public record AlertLevelChangeResponse(
        Long articleId,
        CyberCrisisLevel fromLevel,
        CyberCrisisLevel toLevel,
        AlertLevelChangeDirection direction,
        Instant publishedAt,
        String title,
        String originalUrl
) {
}
