package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 긴급 알림 1건에 묶인 기사 1개: 알림 안 순서, 기사 번호, 이 기사의 알림 종류
 * 기사 엔티티는 다른 모듈이라 번호만 둠 (DB 는 기사 삭제 시 함께 지움)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationArticle {

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private Long articleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertCategory alertCategory;

    public NotificationArticle(int position, Long articleId, AlertCategory alertCategory) {
        this.position = position;
        this.articleId = articleId;
        this.alertCategory = alertCategory;
    }
}
