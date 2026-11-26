package kr.suhsaechan.secbrief.member.service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 기기 관리: 등록·설정 교체·삭제 (푸시 토큰 기준, 토큰은 로그에 남기지 않음)
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
     * 기기 등록: 켠 종류가 없으면 삭제, 있으면 저장 또는 설정 교체
     * 같은 토큰이 동시에 들어오면 유일 제약 위반 > 한 번 다시 (두 번째는 기존 행을 고침)
     */
    public PushDeviceResponse register(PushDeviceRegisterRequest request) {
        try {
            return transactionTemplate.execute(status -> saveOrDelete(request));
        } catch (DataIntegrityViolationException exception) {
            return transactionTemplate.execute(status -> saveOrDelete(request));
        }
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

    private PushDeviceResponse saveOrDelete(PushDeviceRegisterRequest request) {
        Set<AlertCategory> enabledAlertCategories = request.enabledAlertCategories().isEmpty()
                ? EnumSet.noneOf(AlertCategory.class)
                : EnumSet.copyOf(request.enabledAlertCategories());
        Optional<PushDevice> existingDevice = pushDeviceRepository.findByPushToken(request.pushToken());

        if (enabledAlertCategories.isEmpty()) {
            existingDevice.ifPresent(pushDeviceRepository::delete);
            log.info("[push device] unregistered : platform={}", request.platform());
            return new PushDeviceResponse(List.of());
        }
        if (existingDevice.isPresent()) {
            existingDevice.get().updateAlertSettings(request.platform(), enabledAlertCategories);
        } else {
            // 유일 제약 위반을 이 트랜잭션 안에서 드러내려고 바로 반영
            pushDeviceRepository.saveAndFlush(
                    PushDevice.create(request.pushToken(), request.platform(), enabledAlertCategories));
        }
        log.info("[push device] registered : platform={}, categories={}", request.platform(), enabledAlertCategories);
        return new PushDeviceResponse(List.copyOf(enabledAlertCategories));
    }
}
