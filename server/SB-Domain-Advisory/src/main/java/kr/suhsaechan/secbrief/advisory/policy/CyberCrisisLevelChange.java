package kr.suhsaechan.secbrief.advisory.policy;

import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;

/**
 * 경보단계 공지 제목에서 읽은 변경: 바뀌기 전 단계(제목에 없으면 null), 바뀐 단계
 */
public record CyberCrisisLevelChange(CyberCrisisLevel fromLevel, CyberCrisisLevel toLevel) {
}
