package kr.suhsaechan.secbrief.member.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.AlertDeliveryMode;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림을 켠 기기 1대: 푸시 토큰, 플랫폼, 켠 알림 종류, 관심 기술, 받는 방식(바로·아침 요약), 방해 금지 시간
 * 회원이 없어 푸시 토큰이 기기 식별값 (켠 종류도 관심 기술도 없으면 행을 지움)
 */
@Entity
@Table(name = "push_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseEntity {

    public static final LocalTime DEFAULT_DIGEST_TIME = LocalTime.of(8, 0);

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

    // 새 기기는 꺼짐 (사용자가 켜야만 보류함)
    @Embedded
    private QuietHours quietHours = QuietHours.disabledByDefault();

    // 관심 기술 (제품·분야·생태계 code)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "push_device_interest", joinColumns = @JoinColumn(name = "push_device_id"))
    private Set<PushDeviceInterest> interests = new LinkedHashSet<>();

    // 긴급이 아닌 관심 소식 받는 방식 (기본 아침 요약)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertDeliveryMode alertDeliveryMode = AlertDeliveryMode.DIGEST;

    // 아침 요약 시각 (기기 시간대, 기본 08:00)
    @Column(nullable = false)
    private LocalTime digestTime = DEFAULT_DIGEST_TIME;

    // 마지막 아침 요약을 보낸 기기 날짜 (하루 1번)
    private LocalDate lastDigestDate;

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

    public void updateQuietHours(QuietHours quietHours) {
        this.quietHours = quietHours;
    }

    /** 관심 기술 교체 (요청이 준 묶음이 곧 새 상태) */
    public void updateInterests(AlertInterests alertInterests) {
        Set<PushDeviceInterest> requestedInterests = alertInterests.toPushDeviceInterests();
        this.interests.retainAll(requestedInterests);
        this.interests.addAll(requestedInterests);
    }

    public AlertInterests getAlertInterests() {
        return AlertInterests.from(interests);
    }

    public void updateDigestSettings(AlertDeliveryMode alertDeliveryMode, LocalTime digestTime) {
        this.alertDeliveryMode = alertDeliveryMode;
        this.digestTime = digestTime;
    }

    /**
     * 아침 요약을 보낼 때인지: 기기 시각이 요약 시각 이후 + 오늘 아직 안 보냄 + 방해 금지 시간 아님
     */
    public boolean isDigestDueAt(Instant now) {
        ZonedDateTime deviceNow = now.atZone(ZoneId.of(quietHours.getTimeZone()));
        return !deviceNow.toLocalTime().isBefore(digestTime)
                && !deviceNow.toLocalDate().equals(lastDigestDate)
                && !quietHours.isActiveAt(now);
    }

    /** 아침 요약 보냄 표시 (기기 날짜) */
    public void markDigestSent(Instant now) {
        this.lastDigestDate = now.atZone(ZoneId.of(quietHours.getTimeZone())).toLocalDate();
    }

    /** 받을 알림이 하나도 없는지 (켠 종류도 관심 기술도 없음: 행을 지움) */
    public boolean hasNoAlertSubscription() {
        return enabledAlertCategories.isEmpty() && interests.isEmpty();
    }
}
