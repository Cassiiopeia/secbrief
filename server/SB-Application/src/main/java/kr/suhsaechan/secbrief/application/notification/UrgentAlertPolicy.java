package kr.suhsaechan.secbrief.application.notification;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 긴급 알림 규칙: 기사 > 알림 종류 1개, 알릴 만한지, 기사 묶음 > 알림 글 1건 (기사마다 따로 보내지 않고 묶어서 1건)
 * 글에는 제목만 씀 (본문·버전 숫자를 만들지 않음)
 */
public final class UrgentAlertPolicy {

    // Android 알림 채널 (앱이 같은 이름으로 채널을 만듦: mobile 의 알림 설정과 같아야 함)
    public static final String ANDROID_CHANNEL_ID = "urgent-alerts";
    // 알림 data 키·값: 앱이 눌렀을 때 열 탭 (긴급 소식은 모두 취약점 탭에 있음)
    public static final String TARGET_TAB_DATA_KEY = "targetTab";
    public static final String TARGET_TAB_VULNERABILITY = "vulnerability";
    // 알림 data 키: 기사 1건을 알릴 때 그 기사 번호 (앱이 상세 화면을 엶)
    public static final String ARTICLE_ID_DATA_KEY = "articleId";

    /**
     * 알림 글 > 앱이 읽는 data (targetTab 은 항상, articleId 는 1건 알림에만)
     */
    public static Map<String, String> toPushData(UrgentAlertContent content) {
        return content.articleId() == null
                ? Map.of(TARGET_TAB_DATA_KEY, content.targetTab())
                : Map.of(TARGET_TAB_DATA_KEY, content.targetTab(), ARTICLE_ID_DATA_KEY, String.valueOf(content.articleId()));
    }
    // 알림 본문에 싣는 제목 최대 길이 (넘으면 말줄임)
    public static final int MAXIMUM_BODY_TITLE_LENGTH = 120;
    private static final String ELLIPSIS = "…";

    // 여러 건일 때 본문에 먼저 보여 줄 순서: 악용 중 > KISA 긴급 > 고위험
    private static final List<AlertCategory> CATEGORY_PRIORITY =
            List.of(AlertCategory.KEV, AlertCategory.KISA_URGENT, AlertCategory.CRITICAL_CVE);

    private UrgentAlertPolicy() {
    }

    /**
     * 긴급 기사 > 알림 종류: KEV 등재 > KISA 보호나라 공지 > 그 밖(CVSS 9.0 이상, 심각 등급 권고)
     */
    public static AlertCategory toAlertCategory(Article article) {
        if (article.isKev()) {
            return AlertCategory.KEV;
        }
        if (SourceCode.isKisa(article.getSource().getCode())) {
            return AlertCategory.KISA_URGENT;
        }
        return AlertCategory.CRITICAL_CVE;
    }

    /**
     * 알릴 만한 기사인지: 고위험 취약점 중 AI 가 일반(ROUTINE)으로 본 것만 알리지 않음 (잘 안 쓰는 제품의 CVE 가 알림을 채우지 않게)
     * AI 판단이 없으면(대기 기한 초과·실패) 알림 (판단 못 한 소식을 놓치지 않게), KEV·KISA 는 항상 알림
     */
    public static boolean isAlertWorthy(Article article) {
        return toAlertCategory(article) != AlertCategory.CRITICAL_CVE
                || article.getAiStatus() != AiStatus.DONE
                || article.getImportance() != ArticleImportance.ROUTINE;
    }

    /**
     * 기사 묶음(1건 이상) > 알림 글: 1건이면 종류별 제목 + 기사 제목, 여러 건이면 건수 + 가장 중요한 기사 제목
     */
    public static UrgentAlertContent toContent(List<Article> articles) {
        Article leadArticle = articles.stream()
                .min(Comparator.comparingInt((Article article) -> CATEGORY_PRIORITY.indexOf(toAlertCategory(article)))
                        .thenComparing(Article::getPublishedAt, Comparator.reverseOrder()))
                .orElseThrow();
        String leadTitle = toBodyTitle(leadArticle);
        if (articles.size() == 1) {
            return new UrgentAlertContent(toSingleTitle(toAlertCategory(leadArticle)), leadTitle,
                    TARGET_TAB_VULNERABILITY, leadArticle.getId());
        }
        return new UrgentAlertContent("긴급 보안 소식 " + articles.size() + "건이 들어왔어요",
                leadTitle + " 외 " + (articles.size() - 1) + "건", TARGET_TAB_VULNERABILITY, null);
    }

    private static String toSingleTitle(AlertCategory alertCategory) {
        return switch (alertCategory) {
            case KEV -> "악용 중인 취약점이 확인됐어요";
            case KISA_URGENT -> "KISA 긴급 공지가 올라왔어요";
            case CRITICAL_CVE -> "고위험 취약점이 공개됐어요";
        };
    }

    // 표시 제목: 검증된 한국어 제목이 있으면 그것, 없으면 원문 제목 (길면 말줄임)
    private static String toBodyTitle(Article article) {
        String displayTitle = article.getTranslatedTitle() != null ? article.getTranslatedTitle() : article.getTitle();
        return displayTitle.length() <= MAXIMUM_BODY_TITLE_LENGTH
                ? displayTitle
                : displayTitle.substring(0, MAXIMUM_BODY_TITLE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
    }
}
