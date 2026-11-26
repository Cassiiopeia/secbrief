package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 기기에 알릴 기사 1건 + 판정 종류 (알림 기록의 묶음 이름)
 */
public record AlertedArticle(Article article, AlertCategory alertCategory) {
}
