package kr.suhsaechan.secbrief.web.admin.security;

/**
 * 관리자 보안 경로 단일 지점: 로그인·로그아웃·대시보드, 로그인 없이 여는 정적 파일
 */
public final class AdminSecurityPaths {

    public static final String ADMIN_MATCHER = "/admin/**";
    public static final String DASHBOARD = "/admin";
    public static final String LOGIN = "/admin/login";
    public static final String LOGOUT = "/admin/logout";
    // login.html 이 이 파라미터로 안내 문구를 고름
    public static final String LOGIN_ERROR_REDIRECT = LOGIN + "?error";
    public static final String LOGIN_LOCKED_REDIRECT = LOGIN + "?locked";
    public static final String LOGOUT_REDIRECT = LOGIN + "?logout";
    // 로그인 화면이 쓰는 정적 파일 (동봉한 CSS·JS)
    public static final String[] STATIC_MATCHERS = {"/admin/vendor/**", "/admin/js/**", "/admin/css/**"};
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String USERNAME_PARAMETER = "username";

    private AdminSecurityPaths() {
    }
}
