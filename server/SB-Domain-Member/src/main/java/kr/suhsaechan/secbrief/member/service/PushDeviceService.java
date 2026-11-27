package kr.suhsaechan.secbrief.member.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.DigestSettingsResponse;
import kr.suhsaechan.secbrief.member.dto.InterestsResponse;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursResponse;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.entity.QuietHours;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 기기 관리: 등록·설정 교체(켠 종류, 관심 기술, 받는 방식, 방해 금지 시간)·삭제 (푸시 토큰 기준, 토큰은 로그에 남기지 않음)
 */
@Slf4j
@Service
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final TransactionTemplate transactionTemplate;

    public PushDeviceService(PushDeviceRepository pushDeviceRepository, PlatformTransactionManager transactionManager) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 기기 등록: 방해 금지 시간 검증 > 켠 종류·관심 기술이 모두 없으면 삭제, 있으면 저장 또는 설정 교체
     * 같은 토큰이 동시에 들어오면 유일 제약 위반 > 한 번 다시 (두 번째는 기존 행을 고침)
     */
    public PushDeviceResponse register(PushDeviceRegisterRequest request) {
        Optional<QuietHours> requestedQuietHours = Optional.ofNullable(request.quietHours())
                .map(PushDeviceService::toQuietHours);
        try {
            return transactionTemplate.execute(status -> saveOrDelete(request, requestedQuietHours));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> saveOrDelete(request, requestedQuietHours));
        }
    }

    /**
     * 방해 금지 시간 요청 > 값 (모르는 시간대 INVALID_TIME_ZONE, 켰는데 시작 == 끝 QUIET_HOURS_SAME_TIME)
     * 꺼져 있으면 시작 == 끝도 받음 (보류에 쓰지 않음)
     */
    public static QuietHours toQuietHours(QuietHoursRequest request) {
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException exception) {
            throw new CustomException(ErrorCode.INVALID_TIME_ZONE);
        }
        if (request.isEnabled() && request.startTime().equals(request.endTime())) {
            throw new CustomException(ErrorCode.QUIET_HOURS_SAME_TIME);
        }
        return new QuietHours(request.isEnabled(), request.startTime(), request.endTime(), request.timeZone());
    }

    /**
     * 무효 토큰 기기 삭제 (푸시 제공자가 더는 받지 않는다고 알려 준 토큰)
     */
    public int deleteByPushTokens(Collection<String> pushTokens) {
        if (pushTokens.isEmpty()) {
            return 0;
        }
        return transactionTemplate.execute(status -> {
            List<PushDevice> pushDevices = pushDeviceRepository.findAllByPushTokenIn(pushTokens);
            pushDeviceRepository.deleteAll(pushDevices);
            return pushDevices.size();
        });
    }

    // requestedQuietHours 가 비면(이전 앱) 저장된 방해 금지 시간 유지
    // 선택 값이 비면(이전 앱) 저장값 유지, INTEREST 는 켜고 끄는 종류가 아니라 버림
    private PushDeviceResponse saveOrDelete(PushDeviceRegisterRequest request, Optional<QuietHours> requestedQuietHours) {
        Set<AlertCategory> enabledAlertCategories = EnumSet.noneOf(AlertCategory.class);
        request.enabledAlertCategories().stream().filter(AlertCategory::isDeviceToggle).forEach(enabledAlertCategories::add);
        Optional<PushDevice> existingDevice = pushDeviceRepository.findByPushToken(request.pushToken());
        AlertInterests alertInterests = request.interests() != null
                ? request.interests().toAlertInterests()
                : existingDevice.map(PushDevice::getAlertInterests).orElse(AlertInterests.none());

        if (enabledAlertCategories.isEmpty() && alertInterests.isEmpty()) {
            // 기기 행은 지워도 방해 금지 시간·받는 방식은 돌려줌: 요청 값 > 지운 행의 값 > 기본값 (앱이 사용자 설정을 기본값으로 덮지 않게, #119)
            // 관심 기술은 비어 있어야 지우므로 늘 없음
            QuietHours keptQuietHours = requestedQuietHours
                    .or(() -> existingDevice.map(PushDevice::getQuietHours))
                    .orElseGet(QuietHours::disabledByDefault);
            DigestSettingsResponse keptDigest = Optional.ofNullable(request.digest())
                    .map(DigestSettingsResponse::from)
                    .or(() -> existingDevice.map(DigestSettingsResponse::from))
                    .orElseGet(DigestSettingsResponse::defaultSettings);
            existingDevice.ifPresent(pushDeviceRepository::delete);
            log.info("[push device] unregistered : platform={}", request.platform());
            return PushDeviceResponse.unregistered(keptQuietHours, keptDigest);
        }
        PushDevice pushDevice = existingDevice.orElseGet(
                () -> PushDevice.create(request.pushToken(), request.platform(), enabledAlertCategories));
        pushDevice.updateAlertSettings(request.platform(), enabledAlertCategories);
        pushDevice.updateInterests(alertInterests);
        requestedQuietHours.ifPresent(pushDevice::updateQuietHours);
        if (request.digest() != null) {
            pushDevice.updateDigestSettings(request.digest().deliveryMode(), request.digest().digestTime());
        }
        if (existingDevice.isEmpty()) {
            // 유일 제약 위반을 이 트랜잭션 안에서 드러내려고 바로 반영
            pushDeviceRepository.saveAndFlush(pushDevice);
        }
        log.info("[push device] registered : platform={}, categories={}, interests={}, deliveryMode={}, quietHours={}",
                request.platform(), enabledAlertCategories, alertInterests.size(), pushDevice.getAlertDeliveryMode(),
                pushDevice.getQuietHours().isEnabled());
        return new PushDeviceResponse(List.copyOf(enabledAlertCategories),
                QuietHoursResponse.from(pushDevice.getQuietHours()), InterestsResponse.from(alertInterests),
                DigestSettingsResponse.from(pushDevice));
    }
}
