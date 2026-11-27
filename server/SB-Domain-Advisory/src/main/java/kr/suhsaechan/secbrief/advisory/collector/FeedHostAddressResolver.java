package kr.suhsaechan.secbrief.advisory.collector;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

/**
 * 출처 주소 조회: 시스템 DNS 결과 전체 > IPv4 먼저 정렬 (설정 시)
 * 주소 전체를 돌려줘야 HTTP 클라이언트가 연결 실패 시 다음 주소로 넘어감 (ubuntu.com A 레코드 3개 중 1개가 응답 없음, #77)
 */
final class FeedHostAddressResolver implements DnsResolver {

    // IPv4 > IPv6, 같은 종류 안에서는 DNS 순서 유지 (정렬이 안정적)
    private static final Comparator<InetAddress> IPV4_FIRST =
            Comparator.comparingInt(address -> address instanceof Inet4Address ? 0 : 1);

    private final boolean shouldPreferIpv4;
    private final HostAddressLookup hostAddressLookup;

    FeedHostAddressResolver(boolean shouldPreferIpv4) {
        this(shouldPreferIpv4, SystemDefaultDnsResolver.INSTANCE::resolve);
    }

    // 조회 결과를 정해 두는 테스트용
    FeedHostAddressResolver(boolean shouldPreferIpv4, HostAddressLookup hostAddressLookup) {
        this.shouldPreferIpv4 = shouldPreferIpv4;
        this.hostAddressLookup = hostAddressLookup;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = hostAddressLookup.lookup(host);
        if (!shouldPreferIpv4) {
            return addresses;
        }
        return Arrays.stream(addresses).sorted(IPV4_FIRST).toArray(InetAddress[]::new);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return SystemDefaultDnsResolver.INSTANCE.resolveCanonicalHostname(host);
    }

    /**
     * 호스트 이름 > 주소 전체 (운영: 시스템 DNS)
     */
    @FunctionalInterface
    interface HostAddressLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }
}
