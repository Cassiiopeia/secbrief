package kr.suhsaechan.secbrief.advisory.constant;

import java.util.Arrays;
import java.util.Optional;

/**
 * 사이버위기 경보 5단계 (KISA 보호나라 경보단계 안내 기준): 정상 > 관심 > 주의 > 경계 > 심각
 * step 은 1(정상) ~ 5(심각), 화면의 단계 막대 위치
 */
public enum CyberCrisisLevel {
    NORMAL("정상", 1),
    ATTENTION("관심", 2),
    CAUTION("주의", 3),
    ALERT("경계", 4),
    SEVERE("심각", 5);

    private final String koreanName;
    private final int step;

    CyberCrisisLevel(String koreanName, int step) {
        this.koreanName = koreanName;
        this.step = step;
    }

    public String getKoreanName() {
        return koreanName;
    }

    public int getStep() {
        return step;
    }

    /**
     * 한국어 단계 이름 > 단계 (정상·관심·주의·경계·심각 밖이면 비어 있음, 예: 2004년의 '경고')
     */
    public static Optional<CyberCrisisLevel> fromKoreanName(String koreanName) {
        return Arrays.stream(values()).filter(level -> level.koreanName.equals(koreanName)).findFirst();
    }
}
