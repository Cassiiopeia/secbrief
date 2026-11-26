package kr.suhsaechan.secbrief.application.notification;

/**
 * 기기 1대에게 기사 1건을 알리는 방법: IMMEDIATE 바로 푸시, DIGEST 아침 요약에 넣음, NONE 알리지 않음(목록에만)
 */
public enum AlertRoute {
    IMMEDIATE,
    DIGEST,
    NONE
}
