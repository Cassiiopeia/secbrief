package kr.suhsaechan.secbrief.advisory.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 키별 Anthropic SDK 클라이언트 캐시: 키마다 1개를 만들어 다시 씀, 키 값이 바뀌거나 목록에서 빠지면 닫고 버림
 */
@Slf4j
@Component
public class AnthropicClientCache {

    // 요청 1회 제한 시간 (스케줄러 스레드를 오래 잡지 않게)
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    // SDK 자동 재시도 (429·5xx), 넘으면 그 키는 실패 (다음 키로 넘어감)
    private static final int MAXIMUM_RETRY_COUNT = 1;

    private final Function<String, AnthropicClient> clientFactory;
    private final Map<String, CachedClient> cachedClientByKeyId = new ConcurrentHashMap<>();

    @Autowired
    public AnthropicClientCache() {
        this(AnthropicClientCache::createClient);
    }

    // 테스트용: 가짜 클라이언트를 만드는 함수 주입 (실제 호출 없음)
    AnthropicClientCache(Function<String, AnthropicClient> clientFactory) {
        this.clientFactory = clientFactory;
    }

    /** 이 키의 클라이언트 (없거나 키 값이 바뀌었으면 새로 만듦) */
    public AnthropicClient findClient(AnthropicApiKey key) {
        CachedClient cachedClient = cachedClientByKeyId.compute(key.id(), (keyId, previousClient) -> {
            if (previousClient != null && previousClient.apiKey().equals(key.apiKey())) {
                return previousClient;
            }
            close(previousClient);
            return new CachedClient(key.apiKey(), clientFactory.apply(key.apiKey()));
        });
        return cachedClient.client();
    }

    /** 지금 쓰는 키만 남기고 나머지 클라이언트는 닫음 (삭제·끄기·운영 설정 키로 전환) */
    public void retainOnly(Collection<AnthropicApiKey> currentKeys) {
        Set<String> currentKeyIds = currentKeys.stream().map(AnthropicApiKey::id).collect(Collectors.toSet());
        cachedClientByKeyId.keySet().removeIf(keyId -> {
            if (currentKeyIds.contains(keyId)) {
                return false;
            }
            close(cachedClientByKeyId.get(keyId));
            return true;
        });
    }

    private static AnthropicClient createClient(String apiKey) {
        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(MAXIMUM_RETRY_COUNT)
                .build();
    }

    // 닫기 실패는 무시 (연결 풀 정리일 뿐, 호출을 막지 않음)
    private static void close(CachedClient cachedClient) {
        if (cachedClient == null) {
            return;
        }
        try {
            cachedClient.client().close();
        } catch (RuntimeException exception) {
            log.warn("[article ai] client close failed : cause={}", exception.getClass().getSimpleName());
        }
    }

    // apiKey: 키 값이 바뀌었는지 비교용 (로그·예외에 넣지 않음, toString 에서도 뺌)
    private record CachedClient(String apiKey, AnthropicClient client) {

        @Override
        public String toString() {
            return "CachedClient[apiKey=hidden]";
        }
    }
}
