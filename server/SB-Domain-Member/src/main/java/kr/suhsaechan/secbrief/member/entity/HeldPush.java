package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 방해 금지 시간에 보류한 푸시 1건: 그 기기가 받았어야 할 글 그대로 (끝나면 보내고 삭제)
 * 기기·알림함 기록이 지워지면 DB 가 함께 지움
 */
@Entity
@Table(name = "push_device_held_push")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeldPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_device_id", nullable = false)
    private PushDevice pushDevice;

    @Column(nullable = false)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationKind notificationKind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    // 기사 1건 알림이면 그 기사 번호
    private Long articleId;

    @Column(nullable = false)
    private int articleCount;

    @Column(nullable = false)
    private Instant heldAt;

    public static HeldPush create(PushDevice pushDevice, Long notificationId, NotificationKind notificationKind,
                                  String title, String body, Long articleId, int articleCount, Instant heldAt) {
        HeldPush heldPush = new HeldPush();
        heldPush.pushDevice = pushDevice;
        heldPush.notificationId = notificationId;
        heldPush.notificationKind = notificationKind;
        heldPush.title = title;
        heldPush.body = body;
        heldPush.articleId = articleId;
        heldPush.articleCount = articleCount;
        heldPush.heldAt = heldAt;
        return heldPush;
    }
}
