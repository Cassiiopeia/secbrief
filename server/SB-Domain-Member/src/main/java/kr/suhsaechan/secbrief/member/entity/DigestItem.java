package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아침 요약 대기 기사 1건: 기기 + 기사 + 넣은 시각 (요약을 보내면 지움, 48시간 지나면 버림)
 */
@Entity
@Table(name = "push_device_digest_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DigestItem {

    @EmbeddedId
    private DigestItemId id;

    @Column(nullable = false)
    private Instant queuedAt;

    public DigestItem(long pushDeviceId, long articleId, Instant queuedAt) {
        this.id = new DigestItemId(pushDeviceId, articleId);
        this.queuedAt = queuedAt;
    }
}
