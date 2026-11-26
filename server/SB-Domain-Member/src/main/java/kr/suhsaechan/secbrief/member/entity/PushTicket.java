package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발송 티켓 1건: 푸시 제공자가 접수하며 준 번호 (접수증 확인 후 삭제)
 * 접수증이 무효 토큰이라고 하면 기기를 지움 (티켓은 DB 가 함께 지움)
 */
@Entity
@Table(name = "push_ticket")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_device_id", nullable = false)
    private PushDevice pushDevice;

    @Column(nullable = false, length = 64)
    private String ticketId;

    @Column(nullable = false)
    private Instant sentAt;

    public static PushTicket create(PushDevice pushDevice, String ticketId, Instant sentAt) {
        PushTicket pushTicket = new PushTicket();
        pushTicket.pushDevice = pushDevice;
        pushTicket.ticketId = ticketId;
        pushTicket.sentAt = sentAt;
        return pushTicket;
    }
}
