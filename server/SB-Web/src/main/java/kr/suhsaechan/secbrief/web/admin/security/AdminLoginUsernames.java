package kr.suhsaechan.secbrief.web.admin.security;

/**
 * 로그인 화면에서 받은 아이디를 로그·기록에 남길 값으로 정리
 * 등록된 아이디만 그대로 남김 (아이디 칸에 잘못 넣은 비밀번호가 기록에 남지 않게)
 */
public final class AdminLoginUsernames {

    // 등록되지 않은 아이디 대신 남기는 값
    public static final String UNREGISTERED_USERNAME = "(unregistered)";

    private AdminLoginUsernames() {
    }

    public static String toRecordableUsername(String rawUsername, boolean isRegisteredUsername) {
        return isRegisteredUsername && rawUsername != null ? rawUsername.trim() : UNREGISTERED_USERNAME;
    }
}
