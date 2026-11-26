package kr.suhsaechan.secbrief.advisory.ai.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 키 목록 관리 (제공자 1개): 추가(검증·중복·최대 개수), 삭제, 위·아래 이동, 켜기·끄기, 값 교체, 별명 변경, 운영 설정 키 fallback
 * 화면용 목록과 변경 사유에는 키 값이 들어가지 않음 (설정 저장소는 값을 들고 있는 가짜)
 */
class AiApiKeyServiceTest {

    private static final String ACTOR = "admin";
    private static final String FIRST_API_KEY = "sk-test-first-000000000000-AAAA";
    private static final String SECOND_API_KEY = "sk-test-second-00000000000-BBBB";
    private static final String THIRD_API_KEY = "sk-test-third-000000000000-CCCC";

    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final ApiKeyHealthTracker tracker = new ApiKeyHealthTracker(new MutableClock(Instant.parse("2026-11-26T00:00:00Z")));
    // 가짜 설정 저장소가 들고 있는 ANTHROPIC_API_KEYS 평문 (없으면 빈 문자열)
    private String storedSecret = "";
    private final List<String> recordedReasons = new ArrayList<>();
    private AiApiKeyService service;

    @BeforeEach
    void givenInMemoryConfigStore() {
        when(systemConfigService.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).thenAnswer(invocation -> storedSecret);
        when(systemConfigService.update(eq(ConfigKey.ANTHROPIC_API_KEYS), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    storedSecret = invocation.getArgument(1);
                    recordedReasons.add(invocation.getArgument(3));
                    return true;
                });
        when(systemConfigService.resetToDefault(eq(ConfigKey.ANTHROPIC_API_KEYS), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    storedSecret = "";
                    recordedReasons.add(invocation.getArgument(2));
                    return true;
                });
        service = new AiApiKeyService(KeyedAiProvider.ANTHROPIC, systemConfigService, tracker, "");
    }

    @Test
    void addsKeysInPriorityOrderAndStoresDocumentedJsonShape() throws Exception {
        service.add(" 운영 ", " " + FIRST_API_KEY + " ", ACTOR);
        service.add("예비", SECOND_API_KEY, ACTOR);

        List<AiApiKey> storedKeys = service.findStoredKeys();
        assertThat(storedKeys).extracting(AiApiKey::nickname).containsExactly("운영", "예비");
        assertThat(storedKeys).extracting(AiApiKey::apiKey).containsExactly(FIRST_API_KEY, SECOND_API_KEY);
        assertThat(storedKeys).allMatch(AiApiKey::isEnabled);
        assertThat(storedKeys.get(0).id()).hasSize(8);
        // 저장 형식: [{"id","nickname","apiKey","isEnabled"}]
        JsonNode storedJson = new ObjectMapper().readTree(storedSecret);
        assertThat(storedJson.get(0).fieldNames()).toIterable()
                .containsExactlyInAnyOrder("id", "nickname", "apiKey", "isEnabled");
        assertThat(storedJson.get(0).get("isEnabled").asBoolean()).isTrue();
    }

    @Test
    void rejectsInvalidOrDuplicatedInput() {
        service.add("운영", FIRST_API_KEY, ACTOR);

        assertErrorCode(() -> service.add("", SECOND_API_KEY, ACTOR), ErrorCode.AI_KEY_INVALID_NICKNAME);
        assertErrorCode(() -> service.add("가".repeat(31), SECOND_API_KEY, ACTOR), ErrorCode.AI_KEY_INVALID_NICKNAME);
        assertErrorCode(() -> service.add("운영", SECOND_API_KEY, ACTOR), ErrorCode.AI_KEY_DUPLICATED_NICKNAME);
        assertErrorCode(() -> service.add("예비", "too-short", ACTOR), ErrorCode.AI_KEY_INVALID_VALUE);
        assertErrorCode(() -> service.add("예비", "sk-test key with spaces 000000", ACTOR), ErrorCode.AI_KEY_INVALID_VALUE);
        assertErrorCode(() -> service.add("예비", "k".repeat(301), ACTOR), ErrorCode.AI_KEY_INVALID_VALUE);
        assertErrorCode(() -> service.add("예비", FIRST_API_KEY, ACTOR), ErrorCode.AI_KEY_DUPLICATED);
        assertThat(service.findStoredKeys()).hasSize(1);
    }

    @Test
    void rejectedInputNeverPutsKeyValueInExceptionMessage() {
        assertThatThrownBy(() -> service.add("예비", "sk-test key with spaces 000000", ACTOR))
                .hasMessageNotContaining("sk-test");
    }

    @Test
    void acceptsAtMostTenKeys() {
        for (int keyNumber = 1; keyNumber <= AiApiKeyPolicy.MAXIMUM_KEY_COUNT; keyNumber++) {
            service.add("키" + keyNumber, "sk-test-key-number-" + "0".repeat(10) + keyNumber, ACTOR);
        }

        assertErrorCode(() -> service.add("열한째", THIRD_API_KEY, ACTOR), ErrorCode.AI_KEY_LIMIT_EXCEEDED);
        assertThat(service.findStoredKeys()).hasSize(AiApiKeyPolicy.MAXIMUM_KEY_COUNT);
    }

    @Test
    void movesKeyUpAndDownAndKeepsOrderAtEdges() {
        givenThreeKeys();
        String thirdKeyId = findKeyId("셋째");

        service.move(thirdKeyId, true, ACTOR);
        assertThat(findNicknames()).containsExactly("첫째", "셋째", "둘째");
        service.move(thirdKeyId, true, ACTOR);
        assertThat(findNicknames()).containsExactly("셋째", "첫째", "둘째");
        // 맨 위에서 한 번 더 올려도 그대로 (저장도 하지 않음)
        int savedCount = recordedReasons.size();
        service.move(thirdKeyId, true, ACTOR);
        assertThat(findNicknames()).containsExactly("셋째", "첫째", "둘째");
        assertThat(recordedReasons).hasSize(savedCount);

        service.move(thirdKeyId, false, ACTOR);
        assertThat(findNicknames()).containsExactly("첫째", "셋째", "둘째");
    }

    @Test
    void disabledKeyIsNotCallableAndCanBeEnabledAgain() {
        givenThreeKeys();
        String firstKeyId = findKeyId("첫째");

        service.setEnabled(firstKeyId, false, ACTOR);
        assertThat(service.findCallableKeys()).extracting(AiApiKey::nickname).containsExactly("둘째", "셋째");
        assertThat(findNicknames()).containsExactly("첫째", "둘째", "셋째");

        service.setEnabled(firstKeyId, true, ACTOR);
        assertThat(service.findCallableKeys()).extracting(AiApiKey::nickname).containsExactly("첫째", "둘째", "셋째");
    }

    @Test
    void replacesKeyValueKeepingPositionAndNicknameAndForgetsOldHealth() {
        givenThreeKeys();
        String secondKeyId = findKeyId("둘째");
        tracker.recordFailure(secondKeyId, AiFailureKind.UNAUTHORIZED);

        service.replaceApiKey(secondKeyId, "sk-test-reissued-000000000-DDDD", ACTOR);

        assertThat(findNicknames()).containsExactly("첫째", "둘째", "셋째");
        assertThat(service.findStoredKeys().get(1).apiKey()).isEqualTo("sk-test-reissued-000000000-DDDD");
        assertThat(service.findStoredKeys().get(1).id()).isEqualTo(secondKeyId);
        // 새 값은 쿨다운 없이 바로 씀
        assertThat(tracker.isCoolingDown(secondKeyId)).isFalse();
        assertErrorCode(() -> service.replaceApiKey(secondKeyId, FIRST_API_KEY, ACTOR), ErrorCode.AI_KEY_DUPLICATED);
        // 자기 값으로 다시 넣는 것은 중복이 아님
        service.replaceApiKey(secondKeyId, "sk-test-reissued-000000000-DDDD", ACTOR);
    }

    @Test
    void renamesKeyAndRejectsNicknameOfAnotherKey() {
        givenThreeKeys();
        String secondKeyId = findKeyId("둘째");

        assertThat(service.rename(secondKeyId, " 예비 ", ACTOR)).isEqualTo(new AiApiKeyChange(secondKeyId, "예비"));
        assertThat(findNicknames()).containsExactly("첫째", "예비", "셋째");
        assertErrorCode(() -> service.rename(secondKeyId, "첫째", ACTOR), ErrorCode.AI_KEY_DUPLICATED_NICKNAME);
        // 자기 별명 그대로는 중복이 아님
        service.rename(secondKeyId, "예비", ACTOR);
    }

    @Test
    void removesKeyAndClearsStoredSecretWhenLastKeyIsRemoved() {
        service.add("운영", FIRST_API_KEY, ACTOR);
        String keyId = findKeyId("운영");

        assertThat(service.remove(keyId, ACTOR)).isEqualTo(new AiApiKeyChange(keyId, "운영"));

        assertThat(service.findStoredKeys()).isEmpty();
        assertThat(storedSecret).isEmpty();
        verify(systemConfigService).resetToDefault(eq(ConfigKey.ANTHROPIC_API_KEYS), eq(ACTOR), anyString());
        assertErrorCode(() -> service.remove(keyId, ACTOR), ErrorCode.AI_KEY_NOT_FOUND);
    }

    @Test
    void changeReasonsAndViewsNeverContainKeyValues() {
        givenThreeKeys();
        String firstKeyId = findKeyId("첫째");
        service.replaceApiKey(firstKeyId, "sk-test-reissued-000000000-DDDD", ACTOR);
        service.rename(firstKeyId, "운영", ACTOR);
        service.setEnabled(firstKeyId, false, ACTOR);
        service.move(firstKeyId, false, ACTOR);

        assertThat(recordedReasons).isNotEmpty().noneMatch(reason -> reason.contains("sk-test"));
        List<AiApiKeyView> keyViews = service.findStoredKeyViews();
        assertThat(keyViews).extracting(AiApiKeyView::priority).containsExactly(1, 2, 3);
        assertThat(keyViews).extracting(AiApiKeyView::maskedApiKey)
                .containsExactly("••••BBBB", "••••DDDD", "••••CCCC")
                .noneMatch(maskedApiKey -> maskedApiKey.contains("sk-test"));
        assertThat(new AiApiKey("id", "별명", FIRST_API_KEY, true).toString()).doesNotContain(FIRST_API_KEY);
    }

    @Test
    void viewShowsCooldownAndLastFailureKind() {
        service.add("운영", FIRST_API_KEY, ACTOR);
        String keyId = findKeyId("운영");
        tracker.recordFailure(keyId, AiFailureKind.RATE_LIMITED);

        AiApiKeyView keyView = service.findStoredKeyViews().get(0);

        assertThat(keyView.isCoolingDown()).isTrue();
        assertThat(keyView.lastFailureKind()).isEqualTo(AiFailureKind.RATE_LIMITED);
        assertThat(keyView.lastFailureAt()).isNotNull();
        assertThat(keyView.lastSuccessAt()).isNull();
    }

    @Test
    void fallsBackToDeployPropertyKeysOnlyWhenNoStoredKeyIsEnabled() {
        AiApiKeyService serviceWithDeployKeys = new AiApiKeyService(KeyedAiProvider.ANTHROPIC,
                systemConfigService, tracker, " sk-deploy-first-0000000000-EEEE , ,sk-deploy-second-000000000-FFFF,");

        // 설정 저장소에 키 없음: 운영 설정의 키 (쉼표로 여러 개)
        assertThat(serviceWithDeployKeys.isUsingDeployPropertyKeys()).isTrue();
        assertThat(serviceWithDeployKeys.findCallableKeys()).extracting(AiApiKey::nickname)
                .containsExactly("config", "config-2");
        assertThat(serviceWithDeployKeys.findDeployPropertyKeyViews()).extracting(AiApiKeyView::maskedApiKey)
                .containsExactly("••••EEEE", "••••FFFF");

        // 설정 저장소에 켜진 키가 생기면 그 키만
        serviceWithDeployKeys.add("운영", FIRST_API_KEY, ACTOR);
        assertThat(serviceWithDeployKeys.isUsingDeployPropertyKeys()).isFalse();
        assertThat(serviceWithDeployKeys.findCallableKeys()).extracting(AiApiKey::nickname).containsExactly("운영");

        // 전부 끄면 다시 운영 설정의 키
        serviceWithDeployKeys.setEnabled(serviceWithDeployKeys.findStoredKeys().get(0).id(), false, ACTOR);
        assertThat(serviceWithDeployKeys.findCallableKeys()).extracting(AiApiKey::nickname)
                .containsExactly("config", "config-2");
    }

    @Test
    void hasNoCallableKeyWithoutStoredOrDeployPropertyKeys() {
        assertThat(service.hasCallableKey()).isFalse();
        assertThat(service.isUsingDeployPropertyKeys()).isFalse();
        assertThat(service.findDeployPropertyKeyViews()).isEmpty();
    }

    @Test
    void treatsUnreadableStoredValueAsEmptyList() {
        storedSecret = "not a json array";

        assertThat(service.findStoredKeys()).isEmpty();
        assertThat(service.hasCallableKey()).isFalse();
    }

    @Test
    void propagatesMasterKeyMissingWithoutSaving() {
        when(systemConfigService.update(eq(ConfigKey.ANTHROPIC_API_KEYS), anyString(), anyString(), anyString()))
                .thenThrow(new CustomException(ErrorCode.SECRET_MASTER_KEY_MISSING));

        assertErrorCode(() -> service.add("운영", FIRST_API_KEY, ACTOR), ErrorCode.SECRET_MASTER_KEY_MISSING);
        assertThat(service.findStoredKeys()).isEmpty();
        verify(systemConfigService, never()).resetToDefault(any(), any(), any());
    }

    @Test
    void storesEachProviderKeysUnderItsOwnConfigKey() {
        AiApiKeyService openAiService = new AiApiKeyService(KeyedAiProvider.OPENAI, systemConfigService, tracker, "");
        when(systemConfigService.getSecret(ConfigKey.OPENAI_API_KEYS)).thenReturn("");

        openAiService.add("운영", FIRST_API_KEY, ACTOR);

        // OpenAI 키는 OPENAI_API_KEYS 에만 저장, Anthropic 목록은 그대로
        verify(systemConfigService).update(eq(ConfigKey.OPENAI_API_KEYS), anyString(), eq(ACTOR), eq("AI 키 추가: 운영"));
        verify(systemConfigService, never()).update(eq(ConfigKey.ANTHROPIC_API_KEYS), anyString(), anyString(), anyString());
        assertThat(service.findStoredKeys()).isEmpty();
    }

    private void givenThreeKeys() {
        service.add("첫째", FIRST_API_KEY, ACTOR);
        service.add("둘째", SECOND_API_KEY, ACTOR);
        service.add("셋째", THIRD_API_KEY, ACTOR);
    }

    private List<String> findNicknames() {
        return service.findStoredKeys().stream().map(AiApiKey::nickname).toList();
    }

    private String findKeyId(String nickname) {
        return service.findStoredKeys().stream()
                .filter(key -> key.nickname().equals(nickname))
                .findFirst().orElseThrow().id();
    }

    private static void assertErrorCode(Runnable action, ErrorCode expectedErrorCode) {
        assertThatThrownBy(action::run).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(expectedErrorCode);
    }
}
