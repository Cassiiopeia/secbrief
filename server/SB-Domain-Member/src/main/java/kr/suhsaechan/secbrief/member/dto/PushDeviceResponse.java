package kr.suhsaechan.secbrief.member.dto;

import java.util.List;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 알림 기기 등록 결과: 서버에 저장된 켠 알림 종류 (enum 선언 순서, 빈 목록이면 알림 꺼짐)
 */
public record PushDeviceResponse(List<AlertCategory> enabledAlertCategories) {
}
