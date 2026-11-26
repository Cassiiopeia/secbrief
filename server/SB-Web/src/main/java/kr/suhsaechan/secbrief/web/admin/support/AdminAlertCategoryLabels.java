package kr.suhsaechan.secbrief.web.admin.support;

import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import org.springframework.stereotype.Component;

/**
 * 템플릿용 알림 종류 이름·배지 색: ${@adminAlertCategoryLabels.of(종류)}, toneOf(종류) (이름 원본은 AdminDisplayLabels)
 */
@Component("adminAlertCategoryLabels")
public class AdminAlertCategoryLabels {

    public String of(AlertCategory alertCategory) {
        return AdminDisplayLabels.ofAlertCategory(alertCategory);
    }

    /**
     * 배지 색: 악용 중(KEV) error, 그 밖 warning
     */
    public String toneOf(AlertCategory alertCategory) {
        return alertCategory == AlertCategory.KEV ? "error" : "warning";
    }
}
