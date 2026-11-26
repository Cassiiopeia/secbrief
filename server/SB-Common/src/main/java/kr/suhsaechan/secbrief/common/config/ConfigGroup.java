package kr.suhsaechan.secbrief.common.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 설정 묶음 (관리자 설정 화면의 카드 1개, 선언 순서 = 화면 순서)
 */
@Getter
@RequiredArgsConstructor
public enum ConfigGroup {
    AI_PROVIDER("AI 제공자 · 키"),
    AI_PROCESSING("AI 처리량"),
    AI_COST("AI 비용"),
    ALERT("알림");

    private final String label;
}
