package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import kr.suhsaechan.secbrief.member.constant.InterestKind;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기 관심 기술 1개: 종류 + 분류 태그 code (이름이 바뀌어도 code 는 같음)
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeviceInterest {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InterestKind kind;

    @Column(nullable = false, length = 64)
    private String code;

    public PushDeviceInterest(InterestKind kind, String code) {
        this.kind = kind;
        this.code = code;
    }
}
