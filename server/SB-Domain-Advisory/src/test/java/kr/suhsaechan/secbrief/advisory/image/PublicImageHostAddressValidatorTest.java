package kr.suhsaechan.secbrief.advisory.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 접속 주소 검사: 내부·예약 주소 거부 (IPv4, IPv6, IPv6 안에 든 IPv4), 공인 주소 허용 (IP 를 직접 적은 값이라 DNS 조회 없음)
 */
class PublicImageHostAddressValidatorTest {

    private final PublicImageHostAddressValidator validator = new PublicImageHostAddressValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            // 루프백, any-local, 이 네트워크
            "127.0.0.1", "127.255.255.254", "0.0.0.0", "0.1.2.3",
            // 사설·통신사 공유
            "10.0.0.1", "172.16.0.1", "172.31.255.255", "192.168.0.1", "100.64.0.1", "100.127.255.255",
            // 링크로컬 (클라우드 메타데이터 주소)
            "169.254.169.254",
            // 멀티캐스트, 예약, 브로드캐스트, 프로토콜 할당, 시험용
            "224.0.0.1", "240.0.0.1", "255.255.255.255", "192.0.0.8", "198.18.0.1"})
    void blocksInternalIpv4(String address) {
        assertThat(validator.isAllowed(toAddress(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // 루프백, any-local, 링크로컬, 옛 사이트로컬, 고유 로컬, 멀티캐스트
            "::1", "::", "fe80::1", "fec0::1", "fc00::1", "fd12:3456:789a::1", "ff02::1"})
    void blocksInternalIpv6(String address) {
        assertThat(validator.isAllowed(toAddress(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // IPv4-mapped
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254",
            // IPv4 호환, NAT64, 6to4
            "::127.0.0.1", "::192.168.0.1", "64:ff9b::7f00:1", "64:ff9b::a9fe:a9fe", "2002:7f00:1::", "2002:c0a8:1::1"})
    void blocksInternalIpv4EmbeddedInIpv6(String address) {
        assertThat(validator.isAllowed(toAddress(address))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "8.8.8.8", "1.1.1.1", "172.32.0.1", "100.128.0.1", "192.0.1.1", "198.20.0.1",
            "2606:4700:4700::1111", "::ffff:8.8.8.8", "64:ff9b::808:808", "2002:808:808::1"})
    void allowsPublicAddresses(String address) {
        assertThat(validator.isAllowed(toAddress(address))).isTrue();
    }

    private static InetAddress toAddress(String ipLiteral) {
        try {
            return InetAddress.getByName(ipLiteral);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
