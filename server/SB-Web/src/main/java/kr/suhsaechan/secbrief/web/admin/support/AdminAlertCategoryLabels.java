package kr.suhsaechan.secbrief.web.admin.support;

import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import org.springframework.stereotype.Component;

/**
 * 템플릿용 알림 종류 이름: ${@adminAlertCategoryLabels.of(종류)} (이름 원본은 AdminDisplayLabels)
 */
@Component("adminAlertCategoryLabels")
public class AdminAlertCategoryLabels {

    public String of(AlertCategory alertCategory) {
        return AdminDisplayLabels.ofAlertCategory(alertCategory);
    }
}
