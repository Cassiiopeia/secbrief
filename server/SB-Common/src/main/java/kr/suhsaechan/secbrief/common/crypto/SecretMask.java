package kr.suhsaechan.secbrief.common.crypto;

/**
 * 비밀값 가리기 (화면 표시용): 값 > •••• + 끝 4자, 짧은 값은 •••• 만
 */
public final class SecretMask {

    public static final String MASK = "••••";
    private static final int VISIBLE_TAIL_LENGTH = 4;
    // 이 길이보다 짧은 값은 끝자리도 보여 주지 않음 (끝 4자가 값의 큰 부분이 되지 않게)
    private static final int MINIMUM_LENGTH_FOR_TAIL = 12;

    private SecretMask() {
    }

    /** 평문 > 가린 값 (없으면 빈 문자열) */
    public static String mask(String plainValue) {
        if (plainValue == null || plainValue.isEmpty()) {
            return "";
        }
        return plainValue.length() < MINIMUM_LENGTH_FOR_TAIL
                ? MASK
                : MASK + plainValue.substring(plainValue.length() - VISIBLE_TAIL_LENGTH);
    }
}
