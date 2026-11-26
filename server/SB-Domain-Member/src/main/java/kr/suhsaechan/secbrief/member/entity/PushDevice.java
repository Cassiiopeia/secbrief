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
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림을 켠 기기 1대: 푸시 토큰, 플랫폼, 켠 알림 종류
 * 회원이 없어 푸시 토큰이 기기 식별값 (종류를 모두 끄면 행을 지움)
 */
@Entity
@Table(name = "push_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Expo 푸시 토큰 (로그에 남기지 않음)
    @Column(nullable = false, unique = true, length = 255)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PushPlatform platform;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "push_device_alert_category", joinColumns = @JoinColumn(name = "push_device_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_category", nullable = false, length = 32)
    private Set<AlertCategory> enabledAlertCategories = EnumSet.noneOf(AlertCategory.class);

    public static PushDevice create(String pushToken, PushPlatform platform, Set<AlertCategory> enabledAlertCategories) {
        PushDevice pushDevice = new PushDevice();
        pushDevice.pushToken = pushToken;
        pushDevice.updateAlertSettings(platform, enabledAlertCategories);
        return pushDevice;
    }

    /**
     * 알림 설정 교체: 플랫폼 + 켠 종류 전체 (요청이 준 목록이 곧 새 상태)
     */
    public void updateAlertSettings(PushPlatform platform, Set<AlertCategory> enabledAlertCategories) {
        this.platform = platform;
        this.enabledAlertCategories.retainAll(enabledAlertCategories);
        this.enabledAlertCategories.addAll(enabledAlertCategories);
    }
}
