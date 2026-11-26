package kr.suhsaechan.secbrief.common.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설정 저장소 1행: ConfigKey 이름 > 값 (SECRET 은 enc:v1: 암호문만)
 */
@Entity
@Table(name = "system_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ConfigKey 이름 (이 버전이 모르는 이름의 행은 읽을 때 건너뜀)
    @Column(nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(columnDefinition = "text")
    private String configValue;

    public static SystemConfig create(ConfigKey key, String configValue) {
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.configKey = key.name();
        systemConfig.configValue = configValue;
        return systemConfig;
    }

    public void changeValue(String configValue) {
        this.configValue = configValue;
    }
}
