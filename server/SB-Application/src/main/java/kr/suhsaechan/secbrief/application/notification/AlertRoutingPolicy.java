package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;

/**
 * 기기별 알림 판정 (한 곳): 기사 1건 x 기기 설정 > 바로·아침 요약·안 알림
 * 순서: 악용 중(KEV 켬) > KISA 긴급(켬) > 관심 일치(긴급이면 바로, 아니면 받는 방식) > 그 밖의 고위험(켬) > 안 알림
 * 관심 일치: 제품은 그 제품 소식 전부, 분야·생태계는 그중 주요만. 뉴스는 관심 알림에 넣지 않음 (목록에서 봄)
 */
public final class AlertRoutingPolicy {

    private AlertRoutingPolicy() {
    }

    public static AlertRouteDecision route(AlertCandidate candidate, DeviceAlertSettings settings) {
        AlertCategory urgentCategory = candidate.urgentCategory();
        if (urgentCategory == AlertCategory.KEV && settings.enabledAlertCategories().contains(AlertCategory.KEV)) {
            return AlertRouteDecision.immediate(AlertCategory.KEV);
        }
        if (urgentCategory == AlertCategory.KISA_URGENT
                && settings.enabledAlertCategories().contains(AlertCategory.KISA_URGENT)) {
            return AlertRouteDecision.immediate(AlertCategory.KISA_URGENT);
        }
        if (!candidate.isNews() && isInterestMatched(candidate, settings.interests())) {
            if (candidate.isUrgent() || settings.deliveryMode() == AlertDeliveryMode.IMMEDIATE) {
                return AlertRouteDecision.immediate(AlertCategory.INTEREST);
            }
            return AlertRouteDecision.digest();
        }
        if (urgentCategory == AlertCategory.CRITICAL_CVE
                && settings.enabledAlertCategories().contains(AlertCategory.CRITICAL_CVE)) {
            return AlertRouteDecision.immediate(AlertCategory.CRITICAL_CVE);
        }
        return AlertRouteDecision.NONE;
    }

    /** 관심 일치: 제품 code 일치, 또는 주요이면서 분야·생태계 code 일치 */
    public static boolean isInterestMatched(AlertCandidate candidate, AlertInterests interests) {
        if (candidate.productCode() != null && interests.productCodes().contains(candidate.productCode())) {
            return true;
        }
        return candidate.isNotable()
                && ((candidate.fieldCode() != null && interests.fieldCodes().contains(candidate.fieldCode()))
                || (candidate.ecosystemCode() != null && interests.ecosystemCodes().contains(candidate.ecosystemCode())));
    }
}
