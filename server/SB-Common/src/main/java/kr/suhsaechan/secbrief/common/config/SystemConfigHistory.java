package kr.suhsaechan.secbrief.common.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설정 변경 이력 1건: 무엇을, 어떤 값에서 어떤 값으로, 누가, 왜 (SECRET 은 값 대신 있음·없음만)
 */
@Entity
@Table(name = "system_config_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfigHistory {

    // system_config_history.changed_by · reason 열 길이
    public static final int MAXIMUM_CHANGED_BY_LENGTH = 50;
    public static final int MAXIMUM_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String configKey;

    @Column(columnDefinition = "text")
    private String oldValue;

    @Column(columnDefinition = "text")
    private String newValue;

    @Column(nullable = false, length = MAXIMUM_CHANGED_BY_LENGTH)
    private String changedBy;

    @Column(length = MAXIMUM_REASON_LENGTH)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    public static SystemConfigHistory create(ConfigKey key, String oldValue, String newValue, String changedBy,
                                             String reason, Instant createdAt) {
        SystemConfigHistory history = new SystemConfigHistory();
        history.configKey = key.name();
        history.oldValue = oldValue;
        history.newValue = newValue;
        history.changedBy = changedBy;
        history.reason = reason;
        history.createdAt = createdAt;
        return history;
    }
}
