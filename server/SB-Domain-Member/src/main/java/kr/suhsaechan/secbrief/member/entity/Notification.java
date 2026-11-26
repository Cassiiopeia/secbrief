package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림함 1건: 긴급 알림 발송 기록(ALERT, 묶인 기사 목록) 또는 공지사항(NOTICE, 본문·링크·고정)
 * 시각은 밀리초까지만 저장 (목록 커서가 밀리초 단위라 같은 값으로 비교되게)
 */
@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationKind kind;

    // ALERT 의 대표 알림 종류 (NOTICE 는 null)
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AlertCategory alertCategory;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(length = 1000)
    private String linkUrl;

    @Column(nullable = false)
    private boolean isPinned;

    // 푸시를 처음 보낸 시각 (모두 보류 중이거나 푸시 없이 올린 공지는 null)
    private Instant pushedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "notification_article", joinColumns = @JoinColumn(name = "notification_id"))
    @OrderBy("position asc")
    private List<NotificationArticle> articles = new ArrayList<>();

    /**
     * 긴급 알림 기록: 대표 종류, 알림 글, 묶인 기사 (알림 순서대로)
     */
    public static Notification createAlert(AlertCategory alertCategory, String title, String body,
                                           List<NotificationArticle> articles, Instant createdAt) {
        Notification notification = new Notification();
        notification.kind = NotificationKind.ALERT;
        notification.alertCategory = alertCategory;
        notification.title = title;
        notification.body = body;
        notification.articles.addAll(articles);
        notification.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
        notification.updatedAt = notification.createdAt;
        return notification;
    }

    /**
     * 공지사항: 제목, 본문, 선택 링크, 고정 여부
     */
    public static Notification createNotice(String title, String body, String linkUrl, boolean isPinned,
                                            Instant createdAt) {
        Notification notification = new Notification();
        notification.kind = NotificationKind.NOTICE;
        notification.title = title;
        notification.body = body;
        notification.linkUrl = linkUrl;
        notification.isPinned = isPinned;
        notification.createdAt = createdAt.truncatedTo(ChronoUnit.MILLIS);
        notification.updatedAt = notification.createdAt;
        return notification;
    }

    public void updateNotice(String title, String body, String linkUrl, boolean isPinned, Instant updatedAt) {
        this.title = title;
        this.body = body;
        this.linkUrl = linkUrl;
        this.isPinned = isPinned;
        this.updatedAt = updatedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public void changePinned(boolean isPinned, Instant updatedAt) {
        this.isPinned = isPinned;
        this.updatedAt = updatedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * 푸시 보낸 시각: 처음 보낸 시각만 남김 (보류분을 나중에 보내도 바꾸지 않음)
     */
    public void markPushed(Instant pushedAt) {
        if (this.pushedAt == null) {
            this.pushedAt = pushedAt;
        }
    }

    public boolean isNotice() {
        return kind == NotificationKind.NOTICE;
    }
}
