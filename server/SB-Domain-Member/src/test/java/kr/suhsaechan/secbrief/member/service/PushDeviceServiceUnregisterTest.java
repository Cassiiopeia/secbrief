package kr.suhsaechan.secbrief.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.dto.QuietHoursRequest;
import kr.suhsaechan.secbrief.member.dto.QuietHoursResponse;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.entity.QuietHours;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 알림을 모두 끈 등록 (기기 행 삭제): 방해 금지 시간은 요청 값 > 지운 행의 값 > 기본값 순으로 돌려줌 (#119)
 */
class PushDeviceServiceUnregisterTest {

    private static final String PUSH_TOKEN = "ExponentPushToken[quiet-hours-kept]";
    private static final String TIME_ZONE = "Asia/Seoul";

    private final PushDeviceRepository pushDeviceRepository = mock(PushDeviceRepository.class);
    // 트랜잭션은 가짜 관리자 (getTransaction 이 null 상태를 돌려줘도 TransactionTemplate 은 그대로 실행)
    private final PushDeviceService pushDeviceService =
            new PushDeviceService(pushDeviceRepository, mock(PlatformTransactionManager.class));

    @Test
    void keepsRequestedQuietHoursWhenEveryAlertIsTurnedOff() {
        PushDevice existingDevice = existingDeviceWithQuietHours(new QuietHours(true, LocalTime.of(23, 0), LocalTime.of(6, 0), TIME_ZONE));
        given(pushDeviceRepository.findByPushToken(PUSH_TOKEN)).willReturn(Optional.of(existingDevice));

        PushDeviceResponse response = pushDeviceService.register(new PushDeviceRegisterRequest(PUSH_TOKEN, PushPlatform.IOS,
                Set.of(), new QuietHoursRequest(true, LocalTime.of(21, 0), LocalTime.of(7, 0), TIME_ZONE)));

        verify(pushDeviceRepository).delete(existingDevice);
        assertThat(response.enabledAlertCategories()).isEmpty();
        assertThat(response.quietHours()).isEqualTo(new QuietHoursResponse(true, LocalTime.of(21, 0), LocalTime.of(7, 0), TIME_ZONE));
    }

    @Test
    void keepsDeletedDeviceQuietHoursForOldAppWithoutQuietHours() {
        PushDevice existingDevice = existingDeviceWithQuietHours(new QuietHours(false, LocalTime.of(21, 30), LocalTime.of(6, 30), TIME_ZONE));
        given(pushDeviceRepository.findByPushToken(PUSH_TOKEN)).willReturn(Optional.of(existingDevice));

        PushDeviceResponse response = pushDeviceService.register(new PushDeviceRegisterRequest(PUSH_TOKEN, PushPlatform.ANDROID, Set.of()));

        verify(pushDeviceRepository).delete(existingDevice);
        assertThat(response.quietHours()).isEqualTo(new QuietHoursResponse(false, LocalTime.of(21, 30), LocalTime.of(6, 30), TIME_ZONE));
    }

    @Test
    void returnsDefaultQuietHoursWhenNothingIsKnown() {
        given(pushDeviceRepository.findByPushToken(PUSH_TOKEN)).willReturn(Optional.empty());

        PushDeviceResponse response = pushDeviceService.register(new PushDeviceRegisterRequest(PUSH_TOKEN, PushPlatform.IOS, Set.of()));

        assertThat(response.quietHours()).isEqualTo(QuietHoursResponse.from(QuietHours.disabledByDefault()));
    }

    private static PushDevice existingDeviceWithQuietHours(QuietHours quietHours) {
        PushDevice pushDevice = PushDevice.create(PUSH_TOKEN, PushPlatform.IOS, EnumSet.of(AlertCategory.KEV));
        pushDevice.updateQuietHours(quietHours);
        return pushDevice;
    }
}
