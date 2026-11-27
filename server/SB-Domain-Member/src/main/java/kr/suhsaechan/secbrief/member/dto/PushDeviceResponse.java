package kr.suhsaechan.secbrief.member.dto;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;
import kr.suhsaechan.secbrief.member.entity.QuietHours;

/**
 * 알림 기기 등록 결과: 서버에 저장된 켠 알림 종류 (enum 선언 순서) + 방해 금지 시간 + 관심 기술 + 받는 방식
 */
public record PushDeviceResponse(List<AlertCategory> enabledAlertCategories, QuietHoursResponse quietHours,
                                 InterestsResponse interests, DigestSettingsResponse digest) {

    /** 기기를 지웠을 때: 방해 금지 시간·받는 방식은 넘겨받은 값 그대로 (#119), 관심 기술 없음 (지우는 조건) */
    public static PushDeviceResponse unregistered(QuietHours keptQuietHours, DigestSettingsResponse keptDigest) {
        return new PushDeviceResponse(List.of(), QuietHoursResponse.from(keptQuietHours),
                InterestsResponse.from(AlertInterests.none()), keptDigest);
    }
}
