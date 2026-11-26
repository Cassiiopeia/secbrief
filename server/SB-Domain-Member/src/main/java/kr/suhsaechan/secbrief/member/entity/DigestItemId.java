package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아침 요약 대기 키: 기기 번호 + 기사 번호
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DigestItemId implements Serializable {

    @Column(name = "push_device_id", nullable = false)
    private Long pushDeviceId;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    public DigestItemId(long pushDeviceId, long articleId) {
        this.pushDeviceId = pushDeviceId;
        this.articleId = articleId;
    }
}
