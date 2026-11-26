package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;

/**
 * 지금 국내 사이버위기 경보단계: 단계(제목에서 못 읽으면 null, 제목 그대로 보여 줌), 이 단계가 된 시각, 가장 새 경보단계 글
 */
public record AlertLevelStatusResponse(
        CyberCrisisLevel level,
        // 같은 단계가 이어진 첫 글의 게시 시각 (재발령이 있어도 처음 바뀐 날)
        Instant since,
        Long articleId,
        String title,
        String originalUrl
) {
}
