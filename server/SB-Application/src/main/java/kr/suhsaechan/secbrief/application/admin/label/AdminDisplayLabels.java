package kr.suhsaechan.secbrief.application.admin.label;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 관리자 화면 표시 이름 단일 지점: 기사 종류, 수집 상태, 알림 종류 (코드값 > 한국어 이름)
 */
public final class AdminDisplayLabels {

    private static final String NOT_COLLECTED_LABEL = "수집 전";

    private AdminDisplayLabels() {
    }

    /**
     * 기사 종류(NEWS, ADVISORY, VULNERABILITY) > 표시 이름(뉴스, 공지, 취약점)
     */
    public static String ofArticleKind(ArticleKind kind) {
        return switch (kind) {
            case NEWS -> "뉴스";
            case ADVISORY -> "공지";
            case VULNERABILITY -> "취약점";
        };
    }

    /**
     * 수집 상태(SUCCESS, FAILED, null) > 표시 이름(정상, 실패, 수집 전)
     */
    public static String ofCollectStatus(CollectStatus status) {
        if (status == null) {
            return NOT_COLLECTED_LABEL;
        }
        return switch (status) {
            case SUCCESS -> "정상";
            case FAILED -> "실패";
        };
    }

    /**
     * 알림 종류(KISA_URGENT, KEV, CRITICAL_CVE, null) > 표시 이름(KISA 긴급 공지, 악용 중인 취약점, 고위험 취약점, -)
     */
    public static String ofAlertCategory(AlertCategory alertCategory) {
        if (alertCategory == null) {
            return "-";
        }
        return switch (alertCategory) {
            case KISA_URGENT -> "KISA 긴급 공지";
            case KEV -> "악용 중인 취약점";
            case CRITICAL_CVE -> "고위험 취약점";
        };
    }
}
