package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 알림 판정 결과: 방법 + 알림 기록에 붙일 종류 (NONE 이면 종류 null)
 */
public record AlertRouteDecision(AlertRoute route, AlertCategory alertCategory) {

    public static final AlertRouteDecision NONE = new AlertRouteDecision(AlertRoute.NONE, null);

    public static AlertRouteDecision immediate(AlertCategory alertCategory) {
        return new AlertRouteDecision(AlertRoute.IMMEDIATE, alertCategory);
    }

    public static AlertRouteDecision digest() {
        return new AlertRouteDecision(AlertRoute.DIGEST, AlertCategory.INTEREST);
    }
}
