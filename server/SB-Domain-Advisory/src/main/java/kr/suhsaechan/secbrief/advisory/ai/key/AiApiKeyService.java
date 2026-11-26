package kr.suhsaechan.secbrief.advisory.ai.key;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.crypto.SecretMask;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.json.JsonSupport;
import lombok.extern.slf4j.Slf4j;

/**
 * 제공자 1개의 키 목록 관리: 읽기, 추가·삭제·위아래 이동·켜기·끄기·값 교체·별명 변경 (배열 순서 = 우선순위)
 * 저장: 목록 JSON 통째로 제공자의 키 목록 비밀값 한 칸 (암호화·이력은 SystemConfigService), 켜진 키가 없으면 운영 설정의 키를 씀
 * 제공자마다 1개씩 AiApiKeyServiceRegistry 가 만듦 (빈 아님)
 */
@Slf4j
public class AiApiKeyService {

    // 운영 설정의 키에 붙이는 id·별명 (둘째부터 config-2, config-3 ...)
    public static final String DEPLOY_PROPERTY_KEY_LABEL = "config";
    private static final String DEPLOY_PROPERTY_KEY_DELIMITER = ",";
    // 키 id 길이 (UUID 앞부분, 화면 요청에만 씀)
    private static final int KEY_ID_LENGTH = 8;
    private static final TypeReference<List<AiApiKey>> KEY_LIST_TYPE = new TypeReference<>() {
    };

    // 이 제공자의 키 목록 설정 키 (ANTHROPIC_API_KEYS, OPENAI_API_KEYS, GOOGLE_API_KEYS)
    private final ConfigKey apiKeysConfigKey;
    private final SystemConfigService systemConfigService;
    private final ApiKeyHealthTracker apiKeyHealthTracker;
    // 운영 설정의 키 (설정 저장소에 켜진 키가 하나도 없을 때만 씀)
    private final List<AiApiKey> deployPropertyKeys;

    public AiApiKeyService(KeyedAiProvider provider, SystemConfigService systemConfigService,
                           ApiKeyHealthTracker apiKeyHealthTracker, String deployPropertyApiKeys) {
        this.apiKeysConfigKey = provider.getApiKeysConfigKey();
        this.systemConfigService = systemConfigService;
        this.apiKeyHealthTracker = apiKeyHealthTracker;
        this.deployPropertyKeys = toDeployPropertyKeys(deployPropertyApiKeys);
    }

    /** 설정 저장소의 키 전부 (우선순위 순). 없거나 읽을 수 없으면 빈 목록 */
    public List<AiApiKey> findStoredKeys() {
        String storedJson = systemConfigService.getSecret(apiKeysConfigKey);
        if (storedJson.isBlank()) {
            return List.of();
        }
        try {
            List<AiApiKey> storedKeys = JsonSupport.OBJECT_MAPPER.readValue(storedJson, KEY_LIST_TYPE);
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
    public List<AiApiKey> findCallableKeys() {
        List<AiApiKey> enabledStoredKeys = findStoredKeys().stream().filter(AiApiKey::isEnabled).toList();
        return enabledStoredKeys.isEmpty() ? deployPropertyKeys : enabledStoredKeys;
    }

    public boolean hasCallableKey() {
        return !findCallableKeys().isEmpty();
    }

    /** 화면용 목록: 설정 저장소의 키 (가린 값 + 상태) */
    public List<AiApiKeyView> findStoredKeyViews() {
        return toViews(findStoredKeys(), false);
    }

    /** 화면용 목록: 운영 설정의 키 (읽기 전용) */
    public List<AiApiKeyView> findDeployPropertyKeyViews() {
        return toViews(deployPropertyKeys, true);
    }

    /** 지금 운영 설정의 키로 호출하는지 (설정 저장소에 켜진 키가 없고 운영 설정에 키가 있음) */
    public boolean isUsingDeployPropertyKeys() {
        return !deployPropertyKeys.isEmpty() && findStoredKeys().stream().noneMatch(AiApiKey::isEnabled);
    }

    /** 맨 뒤(가장 낮은 우선순위)에 추가 */
    public synchronized AiApiKeyChange add(String nickname, String apiKey, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        AiApiKeyPolicy.requireRoomForNewKey(keys);
        String acceptedNickname = AiApiKeyPolicy.toAcceptedNickname(nickname, keys, null);
        String acceptedApiKey = AiApiKeyPolicy.toAcceptedApiKey(apiKey, keys, null);
        AiApiKey addedKey = new AiApiKey(createKeyId(), acceptedNickname, acceptedApiKey, true);
        keys.add(addedKey);
        save(keys, actor, "AI 키 추가: " + acceptedNickname);
        return toChange(addedKey);
    }

    public synchronized AiApiKeyChange remove(String keyId, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        AiApiKey removedKey = keys.remove(indexOf(keys, keyId));
        save(keys, actor, "AI 키 삭제: " + removedKey.nickname());
        apiKeyHealthTracker.forget(keyId);
        return toChange(removedKey);
    }

    /** 우선순위 한 칸 올리기·내리기 (끝에서는 그대로) */
    public synchronized AiApiKeyChange move(String keyId, boolean isMovingUp, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        int currentIndex = indexOf(keys, keyId);
        int targetIndex = isMovingUp ? currentIndex - 1 : currentIndex + 1;
        AiApiKey movedKey = keys.get(currentIndex);
        if (targetIndex < 0 || targetIndex >= keys.size()) {
            return toChange(movedKey);
        }
        keys.remove(currentIndex);
        keys.add(targetIndex, movedKey);
        save(keys, actor, "AI 키 순서: " + movedKey.nickname() + " > " + (targetIndex + 1) + "순위");
        return toChange(movedKey);
    }

    public synchronized AiApiKeyChange setEnabled(String keyId, boolean isEnabled, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        AiApiKey changedKey = keys.get(keyIndex).withEnabled(isEnabled);
        keys.set(keyIndex, changedKey);
        save(keys, actor, (isEnabled ? "AI 키 켜기: " : "AI 키 끄기: ") + changedKey.nickname());
        return toChange(changedKey);
    }

    /** 같은 자리·별명에 키 값만 교체 (재발급) */
    public synchronized AiApiKeyChange replaceApiKey(String keyId, String apiKey, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        AiApiKey changedKey = keys.get(keyIndex)
                .withApiKey(AiApiKeyPolicy.toAcceptedApiKey(apiKey, keys, keyId));
        keys.set(keyIndex, changedKey);
        save(keys, actor, "AI 키 값 교체: " + changedKey.nickname());
        apiKeyHealthTracker.forget(keyId);
        return toChange(changedKey);
    }

    public synchronized AiApiKeyChange rename(String keyId, String nickname, String actor) {
        List<AiApiKey> keys = new ArrayList<>(findStoredKeys());
        int keyIndex = indexOf(keys, keyId);
        String acceptedNickname = AiApiKeyPolicy.toAcceptedNickname(nickname, keys, keyId);
        String previousNickname = keys.get(keyIndex).nickname();
        keys.set(keyIndex, keys.get(keyIndex).withNickname(acceptedNickname));
        save(keys, actor, "AI 키 별명: " + previousNickname + " > " + acceptedNickname);
        return new AiApiKeyChange(keyId, acceptedNickname);
    }

    // 빈 목록은 비밀값 지우기 (update 는 빈 값을 "그대로 두기"로 받음). reason 에는 별명만 (키 값 금지)
    private void save(List<AiApiKey> keys, String actor, String reason) {
        if (keys.isEmpty()) {
            systemConfigService.resetToDefault(apiKeysConfigKey, actor, reason);
            return;
        }
        try {
            systemConfigService.update(apiKeysConfigKey,
                    JsonSupport.OBJECT_MAPPER.writeValueAsString(keys), actor, reason);
        } catch (JsonProcessingException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "ai key list serialize failed");
        }
    }

    private List<AiApiKeyView> toViews(List<AiApiKey> keys, boolean isFromDeployProperty) {
        List<AiApiKeyView> keyViews = new ArrayList<>();
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            AiApiKey key = keys.get(keyIndex);
            Optional<ApiKeyHealth> health = apiKeyHealthTracker.findHealth(key.id());
            keyViews.add(new AiApiKeyView(key.id(), keyIndex + 1, key.nickname(), SecretMask.mask(key.apiKey()),
                    key.isEnabled(), isFromDeployProperty, apiKeyHealthTracker.isCoolingDown(key.id()),
                    health.map(ApiKeyHealth::lastSuccessAt).orElse(null),
                    health.map(ApiKeyHealth::lastFailureAt).orElse(null),
                    health.map(ApiKeyHealth::lastFailureKind).orElse(null)));
        }
        return keyViews;
    }

    private static AiApiKeyChange toChange(AiApiKey key) {
        return new AiApiKeyChange(key.id(), key.nickname());
    }

    private static int indexOf(List<AiApiKey> keys, String keyId) {
        for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
            if (keys.get(keyIndex).id().equals(keyId)) {
                return keyIndex;
            }
        }
        throw new CustomException(ErrorCode.AI_KEY_NOT_FOUND);
    }

    // 쉼표로 여러 개 가능, 빈 칸·중복은 버림
    private static List<AiApiKey> toDeployPropertyKeys(String deployPropertyApiKeys) {
        List<String> apiKeys = deployPropertyApiKeys == null ? List.of()
                : Arrays.stream(deployPropertyApiKeys.split(DEPLOY_PROPERTY_KEY_DELIMITER))
                        .map(String::strip)
                        .filter(apiKey -> !apiKey.isEmpty())
                        .distinct()
                        .toList();
        List<AiApiKey> keys = new ArrayList<>();
        for (int keyIndex = 0; keyIndex < apiKeys.size(); keyIndex++) {
            String keyLabel = keyIndex == 0 ? DEPLOY_PROPERTY_KEY_LABEL : DEPLOY_PROPERTY_KEY_LABEL + "-" + (keyIndex + 1);
            keys.add(new AiApiKey(keyLabel, keyLabel, apiKeys.get(keyIndex), true));
        }
        return List.copyOf(keys);
    }

    private static String createKeyId() {
        return UUID.randomUUID().toString().substring(0, KEY_ID_LENGTH);
    }
}
