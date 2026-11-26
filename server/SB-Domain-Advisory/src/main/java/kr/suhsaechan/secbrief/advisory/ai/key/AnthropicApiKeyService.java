package kr.suhsaechan.secbrief.advisory.ai.key;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.crypto.SecretMask;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.json.JsonSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Anthropic 키 목록 관리: 읽기, 추가·삭제·위아래 이동·켜기·끄기·값 교체·별명 변경 (배열 순서 = 우선순위)
 * 저장: 목록 JSON 통째로 ANTHROPIC_API_KEYS 비밀값 한 칸 (암호화·이력은 SystemConfigService), 켜진 키가 없으면 운영 설정의 키를 씀
 */
@Slf4j
@Service
public class AnthropicApiKeyService {

    // 운영 설정(secbrief.ai.anthropic-api-key)의 키에 붙이는 id·별명 (둘째부터 config-2, config-3 ...)
    public static final String DEPLOY_PROPERTY_KEY_LABEL = "config";
    private static final String DEPLOY_PROPERTY_KEY_DELIMITER = ",";
    // 키 id 길이 (UUID 앞부분, 화면 요청에만 씀)
    private static final int KEY_ID_LENGTH = 8;
    private static final TypeReference<List<AnthropicApiKey>> KEY_LIST_TYPE = new TypeReference<>() {
    };

    private final SystemConfigService systemConfigService;
    private final ApiKeyHealthTracker apiKeyHealthTracker;
    // 운영 설정의 키 (설정 저장소에 켜진 키가 하나도 없을 때만 씀)
    private final List<AnthropicApiKey> deployPropertyKeys;

    public AnthropicApiKeyService(SystemConfigService systemConfigService, ApiKeyHealthTracker apiKeyHealthTracker,
                                  @Value("${secbrief.ai.anthropic-api-key:}") String deployPropertyApiKeys) {
        this.systemConfigService = systemConfigService;
        this.apiKeyHealthTracker = apiKeyHealthTracker;
        this.deployPropertyKeys = toDeployPropertyKeys(deployPropertyApiKeys);
    }

    /** 설정 저장소의 키 전부 (우선순위 순). 없거나 읽을 수 없으면 빈 목록 */
    public List<AnthropicApiKey> findStoredKeys() {
        String storedJson = systemConfigService.getSecret(ConfigKey.ANTHROPIC_API_KEYS);
        if (storedJson.isBlank()) {
            return List.of();
        }
        try {
            List<AnthropicApiKey> storedKeys = JsonSupport.OBJECT_MAPPER.readValue(storedJson, KEY_LIST_TYPE);
            return storedKeys == null ? List.of() : storedKeys.stream()
                    .filter(key -> key != null && key.id() != null && key.nickname() != null
                            && key.apiKey() != null && !key.apiKey().isBlank())
                    .toList();
        } catch (JsonProcessingException exception) {
            // 원문(키 값 포함)은 남기지 않음
            log.warn("[ai key] stored key list unreadable, treated as empty");
            return List.of();
        }
    }

    /** 호출에 쓸 키 (우선순위 순): 설정 저장소의 켜진 키, 하나도 없으면 운영 설정의 키 */
    public List<AnthropicApiKey> findCallableKeys() {
        List<AnthropicApiKey> enabledStoredKeys = findStoredKeys().stream().filter(AnthropicApiKey::isEnabled).toList();
        return enabledStoredKeys.isEmpty() ? deployPropertyKeys : enabledStoredKeys;
    }

    public boolean hasCallableKey() {
        return !findCallableKeys().isEmpty();
    }

    /** 화면용 목록: 설정 저장소의 키 (가린 값 + 상태) */
    public List<AnthropicApiKeyView> findStoredKeyViews() {
        return toViews(findStoredKeys(), false);
    }

    /** 화면용 목록: 운영 설정의 키 (읽기 전용) */
    public List<AnthropicApiKeyView> findDeployPropertyKeyViews() {
        return toViews(deployPropertyKeys, true);
    }

    /** 지금 운영 설정의 키로 호출하는지 (설정 저장소에 켜진 키가 없고 운영 설정에 키가 있음) */
    public boolean isUsingDeployPropertyKeys() {
        return !deployPropertyKeys.isEmpty() && findStoredKeys().stream().noneMatch(AnthropicApiKey::isEnabled);
    }

    /** 맨 뒤(가장 낮은 우선순위)에 추가 */
    public synchronized AnthropicApiKeyChange add(String nickname, String apiKey, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        AnthropicApiKeyPolicy.requireRoomForNewKey(keys);
        String acceptedNickname = AnthropicApiKeyPolicy.toAcceptedNickname(nickname, keys, null);
        String acceptedApiKey = AnthropicApiKeyPolicy.toAcceptedApiKey(apiKey, keys, null);
        AnthropicApiKey addedKey = new AnthropicApiKey(createKeyId(), acceptedNickname, acceptedApiKey, true);
        keys.add(addedKey);
        save(keys, actor, "AI 키 추가: " + acceptedNickname);
        return toChange(addedKey);
    }

    public synchronized AnthropicApiKeyChange remove(String keyId, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        AnthropicApiKey removedKey = keys.remove(indexOf(keys, keyId));
        save(keys, actor, "AI 키 삭제: " + removedKey.nickname());
        apiKeyHealthTracker.forget(keyId);
        return toChange(removedKey);
    }

    /** 우선순위 한 칸 올리기·내리기 (끝에서는 그대로) */
    public synchronized AnthropicApiKeyChange move(String keyId, boolean isMovingUp, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        int currentIndex = indexOf(keys, keyId);
        int targetIndex = isMovingUp ? currentIndex - 1 : currentIndex + 1;
        AnthropicApiKey movedKey = keys.get(currentIndex);
        if (targetIndex < 0 || targetIndex >= keys.size()) {
            return toChange(movedKey);
        }
        keys.remove(currentIndex);
        keys.add(targetIndex, movedKey);
        save(keys, actor, "AI 키 순서: " + movedKey.nickname() + " > " + (targetIndex + 1) + "순위");
        return toChange(movedKey);
    }

    public synchronized AnthropicApiKeyChange setEnabled(String keyId, boolean isEnabled, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        AnthropicApiKey changedKey = keys.get(keyIndex).withEnabled(isEnabled);
        keys.set(keyIndex, changedKey);
        save(keys, actor, (isEnabled ? "AI 키 켜기: " : "AI 키 끄기: ") + changedKey.nickname());
        return toChange(changedKey);
    }

    /** 같은 자리·별명에 키 값만 교체 (재발급) */
    public synchronized AnthropicApiKeyChange replaceApiKey(String keyId, String apiKey, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        AnthropicApiKey changedKey = keys.get(keyIndex)
                .withApiKey(AnthropicApiKeyPolicy.toAcceptedApiKey(apiKey, keys, keyId));
        keys.set(keyIndex, changedKey);
        save(keys, actor, "AI 키 값 교체: " + changedKey.nickname());
        apiKeyHealthTracker.forget(keyId);
        return toChange(changedKey);
    }

    public synchronized AnthropicApiKeyChange rename(String keyId, String nickname, String actor) {
        List<AnthropicApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        String acceptedNickname = AnthropicApiKeyPolicy.toAcceptedNickname(nickname, keys, keyId);
        String previousNickname = keys.get(keyIndex).nickname();
        keys.set(keyIndex, keys.get(keyIndex).withNickname(acceptedNickname));
        save(keys, actor, "AI 키 별명: " + previousNickname + " > " + acceptedNickname);
        return new AnthropicApiKeyChange(keyId, acceptedNickname);
    }

    // 빈 목록은 비밀값 지우기 (update 는 빈 값을 "그대로 두기"로 받음). reason 에는 별명만 (키 값 금지)
    private void save(List<AnthropicApiKey> keys, String actor, String reason) {
        if (keys.isEmpty()) {
            systemConfigService.resetToDefault(ConfigKey.ANTHROPIC_API_KEYS, actor, reason);
            return;
        }
        try {
            systemConfigService.update(ConfigKey.ANTHROPIC_API_KEYS,
                    JsonSupport.OBJECT_MAPPER.writeValueAsString(keys), actor, reason);
        } catch (JsonProcessingException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "ai key list serialize failed");
        }
    }

    private List<AnthropicApiKeyView> toViews(List<AnthropicApiKey> keys, boolean isFromDeployProperty) {
        List<AnthropicApiKeyView> keyViews = new ArrayList<>();
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            AnthropicApiKey key = keys.get(keyIndex);
            Optional<ApiKeyHealth> health = apiKeyHealthTracker.findHealth(key.id());
            keyViews.add(new AnthropicApiKeyView(key.id(), keyIndex + 1, key.nickname(), SecretMask.mask(key.apiKey()),
                    key.isEnabled(), isFromDeployProperty, apiKeyHealthTracker.isCoolingDown(key.id()),
                    health.map(ApiKeyHealth::lastSuccessAt).orElse(null),
                    health.map(ApiKeyHealth::lastFailureAt).orElse(null),
                    health.map(ApiKeyHealth::lastFailureKind).orElse(null)));
        }
        return keyViews;
    }

    private static AnthropicApiKeyChange toChange(AnthropicApiKey key) {
        return new AnthropicApiKeyChange(key.id(), key.nickname());
    }

    private static int indexOf(List<AnthropicApiKey> keys, String keyId) {
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            if (keys.get(keyIndex).id().equals(keyId)) {
                return keyIndex;
            }
        }
        throw new CustomException(ErrorCode.AI_KEY_NOT_FOUND);
    }

    // 쉼표로 여러 개 가능, 빈 칸·중복은 버림
    private static List<AnthropicApiKey> toDeployPropertyKeys(String deployPropertyApiKeys) {
        List<String> apiKeys = deployPropertyApiKeys == null ? List.of()
                : Arrays.stream(deployPropertyApiKeys.split(DEPLOY_PROPERTY_KEY_DELIMITER))
                        .map(String::strip)
                        .filter(apiKey -> !apiKey.isEmpty())
                        .distinct()
                        .toList();
        List<AnthropicApiKey> keys = new ArrayList<>();
        for (int keyIndex = 0; keyIndex < apiKeys.size(); keyIndex++) {
            String keyLabel = keyIndex == 0 ? DEPLOY_PROPERTY_KEY_LABEL : DEPLOY_PROPERTY_KEY_LABEL + "-" + (keyIndex + 1);
            keys.add(new AnthropicApiKey(keyLabel, keyLabel, apiKeys.get(keyIndex), true));
        }
        return List.copyOf(keys);
    }

    private static String createKeyId() {
        return UUID.randomUUID().toString().substring(0, KEY_ID_LENGTH);
    }
}
