package kr.suhsaechan.secbrief.advisory.image;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 공인 주소만 허용 (SSRF 방어): 내부망·자기 자신·클라우드 메타데이터로 가는 주소 거부
 * 거부 6종: any-local, 루프백, 링크로컬, 사설(사이트로컬·IPv6 고유 로컬·통신사 공유 대역), 멀티캐스트, 예약 대역
 */
@Component
public class PublicImageHostAddressValidator implements ImageHostAddressValidator {

    private static final int IPV4_BYTE_LENGTH = 4;
    private static final int IPV6_BYTE_LENGTH = 16;
    // NAT64 잘 알려진 접두사 64:ff9b::/96 (뒤 4바이트가 IPv4)
    private static final byte[] NAT64_PREFIX = {0x00, 0x64, (byte) 0xff, (byte) 0x9b, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] IPV4_COMPATIBLE_PREFIX = new byte[12];

    @Override
    public boolean isAllowed(InetAddress address) {
        if (address instanceof Inet6Address ipv6Address) {
            return isPublicIpv6(ipv6Address);
        }
        return isPublicIpv4(address);
    }

    private static boolean isPublicIpv4(InetAddress address) {
        if (isLocalByJavaClassification(address)) {
            return false;
        }
        byte[] addressBytes = address.getAddress();
        int firstOctet = addressBytes[0] & 0xff;
        int secondOctet = addressBytes[1] & 0xff;
        int thirdOctet = addressBytes[2] & 0xff;
        // 0.0.0.0/8 (이 네트워크), 100.64.0.0/10 (통신사 공유), 192.0.0.0/24 (프로토콜 할당), 198.18.0.0/15 (시험용), 240.0.0.0/4 (예약·브로드캐스트)
        boolean isReserved = firstOctet == 0
                || (firstOctet == 100 && secondOctet >= 64 && secondOctet <= 127)
                || (firstOctet == 192 && secondOctet == 0 && thirdOctet == 0)
                || (firstOctet == 198 && (secondOctet == 18 || secondOctet == 19))
                || firstOctet >= 240;
        return !isReserved;
    }

    private static boolean isPublicIpv6(Inet6Address address) {
        if (isLocalByJavaClassification(address)) {
            return false;
        }
        byte[] addressBytes = address.getAddress();
        // fc00::/7 고유 로컬 (자바 isSiteLocalAddress 는 옛 fec0::/10 만 봄)
        if ((addressBytes[0] & 0xfe) == 0xfc) {
            return false;
        }
        // IPv6 안에 든 IPv4 는 꺼내서 IPv4 규칙으로 다시 검사 (IPv4-mapped 는 자바가 IPv4 로 바꿔 줌)
        return toEmbeddedIpv4(addressBytes).map(PublicImageHostAddressValidator::isPublicIpv4).orElse(true);
    }

    // any-local, 루프백, 링크로컬(169.254.0.0/16 메타데이터 주소 포함), 사이트로컬(10·172.16·192.168), 멀티캐스트
    private static boolean isLocalByJavaClassification(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    // IPv4 호환(::a.b.c.d)·NAT64(64:ff9b::a.b.c.d) 는 뒤 4바이트, 6to4(2002:aabb:ccdd::) 는 3~6번째 바이트
    private static Optional<InetAddress> toEmbeddedIpv4(byte[] ipv6Bytes) {
        if (hasPrefix(ipv6Bytes, IPV4_COMPATIBLE_PREFIX) || hasPrefix(ipv6Bytes, NAT64_PREFIX)) {
            return Optional.of(toIpv4(Arrays.copyOfRange(ipv6Bytes, IPV6_BYTE_LENGTH - IPV4_BYTE_LENGTH, IPV6_BYTE_LENGTH)));
        }
        if (ipv6Bytes[0] == 0x20 && ipv6Bytes[1] == 0x02) {
            return Optional.of(toIpv4(Arrays.copyOfRange(ipv6Bytes, 2, 2 + IPV4_BYTE_LENGTH)));
        }
        return Optional.empty();
    }

    private static boolean hasPrefix(byte[] addressBytes, byte[] prefix) {
        return Arrays.equals(addressBytes, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static InetAddress toIpv4(byte[] ipv4Bytes) {
        try {
            return InetAddress.getByAddress(ipv4Bytes);
        } catch (UnknownHostException exception) {
            // 길이 4 고정이라 일어나지 않음
            throw new IllegalStateException(exception);
        }
    }
}
