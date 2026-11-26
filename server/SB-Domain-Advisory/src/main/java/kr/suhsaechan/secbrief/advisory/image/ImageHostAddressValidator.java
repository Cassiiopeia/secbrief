package kr.suhsaechan.secbrief.advisory.image;

import java.net.InetAddress;

/**
 * 이미지 내려받기 접속 주소 검사 규약: 접속해도 되는 주소인지 판정
 * 운영 구현은 공인 주소만 허용 (PublicImageHostAddressValidator), 로컬 가짜 서버를 쓰는 테스트만 규칙을 바꿈
 */
public interface ImageHostAddressValidator {

    boolean isAllowed(InetAddress address);
}
