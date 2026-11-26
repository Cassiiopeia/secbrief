package kr.suhsaechan.secbrief.member.constant;

/**
 * 긴급이 아닌 관심 기술 소식을 받는 방식 (이름이 그대로 API 값·DB 값): IMMEDIATE 바로, DIGEST 하루 1번 아침 요약
 */
public enum AlertDeliveryMode {
    IMMEDIATE,
    DIGEST
}
