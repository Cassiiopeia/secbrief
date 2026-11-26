package kr.suhsaechan.secbrief.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import org.junit.jupiter.api.Test;

/**
 * 기기별 알림 판정: 악용 중·KISA 긴급 바로, 관심 일치(긴급이면 바로, 아니면 받는 방식), 그 밖의 고위험(켬), 나머지 안 알림
 */
class AlertRoutingPolicyTest {

    private static final AlertInterests FORTINET_AND_NETWORK_AND_MAVEN =
            new AlertInterests(Set.of("fortinet"), Set.of("network"), Set.of("maven"));
    private static final DeviceAlertSettings ESSENTIALS_ONLY = new DeviceAlertSettings(
            EnumSet.of(AlertCategory.KEV, AlertCategory.KISA_URGENT), AlertInterests.none(), AlertDeliveryMode.DIGEST);
    private static final DeviceAlertSettings INTERESTED_DIGEST = new DeviceAlertSettings(
            EnumSet.of(AlertCategory.KEV, AlertCategory.KISA_URGENT), FORTINET_AND_NETWORK_AND_MAVEN, AlertDeliveryMode.DIGEST);
    private static final DeviceAlertSettings INTERESTED_IMMEDIATE = new DeviceAlertSettings(
            EnumSet.of(AlertCategory.KEV), FORTINET_AND_NETWORK_AND_MAVEN, AlertDeliveryMode.IMMEDIATE);
    private static final DeviceAlertSettings OTHER_CRITICAL_ON = new DeviceAlertSettings(
            EnumSet.of(AlertCategory.KEV, AlertCategory.KISA_URGENT, AlertCategory.CRITICAL_CVE), AlertInterests.none(),
            AlertDeliveryMode.DIGEST);

    @Test
    void sendsExploitedAndKisaUrgentImmediatelyEvenWithoutInterests() {
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.KEV, true, null, null, null, true), ESSENTIALS_ONLY))
                .isEqualTo(AlertRouteDecision.immediate(AlertCategory.KEV));
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.KISA_URGENT, true, null, null, null, true), ESSENTIALS_ONLY))
                .isEqualTo(AlertRouteDecision.immediate(AlertCategory.KISA_URGENT));
        // 관심 기술을 안 골랐으면 고위험 CVE·관심 밖 소식은 알리지 않음 (꼭 필요한 것만)
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.CRITICAL_CVE, true, "fortinet", "network", null, true),
                ESSENTIALS_ONLY)).isEqualTo(AlertRouteDecision.NONE);
    }

    @Test
    void sendsProductMatchesAndNotableFieldOrEcosystemMatchesByDeliveryMode() {
        // 제품 일치: 주요가 아니어도, 긴급이 아니면 아침 요약
        assertThat(AlertRoutingPolicy.route(candidate(null, false, "fortinet", "network", null, false), INTERESTED_DIGEST))
                .isEqualTo(AlertRouteDecision.digest());
        // 제품 일치 + 긴급(CVSS 9.0 이상): 바로
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.CRITICAL_CVE, true, "fortinet", "network", null, true),
                INTERESTED_DIGEST)).isEqualTo(AlertRouteDecision.immediate(AlertCategory.INTEREST));
        // 분야·생태계는 주요만
        assertThat(AlertRoutingPolicy.route(candidate(null, false, null, "network", null, false), INTERESTED_DIGEST))
                .isEqualTo(AlertRouteDecision.NONE);
        assertThat(AlertRoutingPolicy.route(candidate(null, false, "gradle", "dev-tool", "maven", true), INTERESTED_DIGEST))
                .isEqualTo(AlertRouteDecision.digest());
        // 바로 받기를 고르면 긴급이 아니어도 바로
        assertThat(AlertRoutingPolicy.route(candidate(null, false, null, "network", null, true), INTERESTED_IMMEDIATE))
                .isEqualTo(AlertRouteDecision.immediate(AlertCategory.INTEREST));
        // 관심 밖 생태계(처음 보는 npm 패키지)는 안 알림
        assertThat(AlertRoutingPolicy.route(candidate(null, false, null, "web-app", "npm", false), INTERESTED_DIGEST))
                .isEqualTo(AlertRouteDecision.NONE);
    }

    @Test
    void keepsExploitedFirstAndHandlesOtherCriticalToggleAndNews() {
        // KEV 를 끈 기기라도 관심 제품이면 관심으로 바로
        DeviceAlertSettings kevOffInterested = new DeviceAlertSettings(EnumSet.noneOf(AlertCategory.class),
                FORTINET_AND_NETWORK_AND_MAVEN, AlertDeliveryMode.DIGEST);
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.KEV, true, "fortinet", "network", null, true),
                kevOffInterested)).isEqualTo(AlertRouteDecision.immediate(AlertCategory.INTEREST));
        // 그 밖의 고위험을 켜면 관심 밖 고위험도 바로
        assertThat(AlertRoutingPolicy.route(candidate(AlertCategory.CRITICAL_CVE, true, null, "web-app", null, false),
                OTHER_CRITICAL_ON)).isEqualTo(AlertRouteDecision.immediate(AlertCategory.CRITICAL_CVE));
        // AI·규칙이 알릴 만하지 않다고 본 고위험(긴급 종류 없음)은 그 밖의 고위험을 켜도 안 알림
        assertThat(AlertRoutingPolicy.route(candidate(null, true, null, "web-app", null, false), OTHER_CRITICAL_ON))
                .isEqualTo(AlertRouteDecision.NONE);
        // 뉴스는 관심 알림에 넣지 않음
        AlertCandidate news = new AlertCandidate(null, null, false, true, "fortinet", "network", null, true);
        assertThat(AlertRoutingPolicy.route(news, INTERESTED_IMMEDIATE)).isEqualTo(AlertRouteDecision.NONE);
    }

    private static AlertCandidate candidate(AlertCategory urgentCategory, boolean isUrgent, String productCode,
                                            String fieldCode, String ecosystemCode, boolean isNotable) {
        return new AlertCandidate(null, urgentCategory, isUrgent, false, productCode, fieldCode, ecosystemCode, isNotable);
    }
}
