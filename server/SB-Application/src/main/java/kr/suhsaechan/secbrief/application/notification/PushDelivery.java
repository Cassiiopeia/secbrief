package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.member.entity.PushDevice;

/**
 * 기기 1대에 보낼 글 1건 (방해 금지 시간이면 보류 대상)
 */
public record PushDelivery(PushDevice pushDevice, PushContent pushContent) {
}
