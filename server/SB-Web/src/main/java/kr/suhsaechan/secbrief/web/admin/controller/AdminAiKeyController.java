package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import kr.suhsaechan.secbrief.advisory.ai.AiProviderModelService;
import kr.suhsaechan.secbrief.advisory.ai.AiProviderPriorityService;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyChange;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyPolicy;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyServiceRegistry;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyView;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.admin.support.AdminAiKeyProviderSection;
import kr.suhsaechan.secbrief.web.admin.support.AdminFlash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * AI 키 화면: 제공자(Anthropic·OpenAI·Google)별 키 표(가린 값·상태)와 추가·삭제·위아래 이동·켜기·끄기·값 교체·별명 변경, 제공자 우선순위
 * 전부 폼 POST > 리다이렉트, 주소에 제공자 코드가 들어감 (/admin/ai-keys/{providerCode}/...)
 * 키 값은 받기만 하고 내려보내지 않음. 감사 기록에는 키 id 와 제공자·별명만 남김
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminAiKeyController {

    private static final String AI_KEYS_PATH = "/admin/ai-keys";
    private static final String PROVIDER_KEYS_PATH = AI_KEYS_PATH + "/{providerCode}";
    private static final String AI_KEYS_VIEW = "admin/ai-keys";
    private static final String REDIRECT_TO_AI_KEYS = "redirect:" + AI_KEYS_PATH;
    // move 요청의 direction 값: up 위로, 그 외 아래로
    private static final String DIRECTION_UP = "up";

    private final AiApiKeyServiceRegistry aiApiKeyServiceRegistry;
    private final AiProviderModelService aiProviderModelService;
    private final AiProviderPriorityService aiProviderPriorityService;
    private final SystemConfigService systemConfigService;
    private final AdminAuditService adminAuditService;

    @GetMapping(AI_KEYS_PATH)
    public String showAiKeys(Model model) {
        boolean canStoreSecret = systemConfigService.canStoreSecret();
        model.addAttribute("providerSections", Arrays.stream(KeyedAiProvider.values())
                .map(provider -> toProviderSection(provider, canStoreSecret))
                .toList());
        model.addAttribute("canStoreSecret", canStoreSecret);
        model.addAttribute("maximumKeyCount", AiApiKeyPolicy.MAXIMUM_KEY_COUNT);
        model.addAttribute("maximumNicknameLength", AiApiKeyPolicy.MAXIMUM_NICKNAME_LENGTH);
        model.addAttribute("minimumApiKeyLength", AiApiKeyPolicy.MINIMUM_API_KEY_LENGTH);
        model.addAttribute("maximumApiKeyLength", AiApiKeyPolicy.MAXIMUM_API_KEY_LENGTH);
        model.addAttribute("providerViews", aiProviderPriorityService.findProviderViews());
        model.addAttribute("priorityCodes", aiProviderPriorityService.findPriorityCodes());
        return AI_KEYS_VIEW;
    }

    @PostMapping(PROVIDER_KEYS_PATH)
    public String addKey(@PathVariable String providerCode, @RequestParam(required = false) String nickname,
                         @RequestParam(required = false) String apiKey,
                         Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).add(nickname, apiKey, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_ADD, provider, change, "");
            return toKeyLabel(provider, change) + " 키를 추가했어요";
        });
    }

    @PostMapping(PROVIDER_KEYS_PATH + "/{keyId}/remove")
    public String removeKey(@PathVariable String providerCode, @PathVariable String keyId,
                            Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).remove(keyId, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_REMOVE, provider, change, "");
            return toKeyLabel(provider, change) + " 키를 삭제했어요";
        });
    }

    @PostMapping(PROVIDER_KEYS_PATH + "/{keyId}/move")
    public String moveKey(@PathVariable String providerCode, @PathVariable String keyId,
                          @RequestParam(required = false) String direction,
                          Principal principal, RedirectAttributes redirectAttributes) {
        boolean isMovingUp = DIRECTION_UP.equals(direction);
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).move(keyId, isMovingUp, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_MOVE, provider, change, isMovingUp ? " 위로" : " 아래로");
            return toKeyLabel(provider, change) + " 키의 우선순위를 " + (isMovingUp ? "올렸어요" : "내렸어요");
        });
    }

    @PostMapping(PROVIDER_KEYS_PATH + "/{keyId}/toggle")
    public String toggleKey(@PathVariable String providerCode, @PathVariable String keyId,
                            @RequestParam(name = "enabled", defaultValue = "false") boolean isEnabled,
                            Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).setEnabled(keyId, isEnabled, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_TOGGLE, provider, change, isEnabled ? " 켬" : " 끔");
            return toKeyLabel(provider, change) + " 키를 " + (isEnabled ? "켰어요" : "껐어요");
        });
    }

    @PostMapping(PROVIDER_KEYS_PATH + "/{keyId}/replace")
    public String replaceKey(@PathVariable String providerCode, @PathVariable String keyId,
                             @RequestParam(required = false) String apiKey,
                             Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).replaceApiKey(keyId, apiKey, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_REPLACE, provider, change, "");
            return toKeyLabel(provider, change) + " 키 값을 바꿨어요";
        });
    }

    @PostMapping(PROVIDER_KEYS_PATH + "/{keyId}/rename")
    public String renameKey(@PathVariable String providerCode, @PathVariable String keyId,
                            @RequestParam(required = false) String nickname,
                            Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(providerCode, redirectAttributes, provider -> {
            AiApiKeyChange change = findKeyService(provider).rename(keyId, nickname, principal.getName());
            recordAudit(principal, AdminAuditAction.AI_KEY_RENAME, provider, change, "");
            return provider.getDisplayName() + " 키 별명을 " + change.nickname() + "(으)로 바꿨어요";
        });
    }

    /**
     * 제공자 우선순위 저장: providerCodes 순서 = 우선순위 (빈 칸은 버림)
     */
    @PostMapping(AI_KEYS_PATH + "/provider-priority")
    public String updateProviderPriority(@RequestParam(name = "providerCodes", required = false) List<String> providerCodes,
                                         Principal principal, RedirectAttributes redirectAttributes) {
        return applyChange(redirectAttributes, REDIRECT_TO_AI_KEYS, () -> {
            String previousValue = systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY);
            String savedValue = aiProviderPriorityService.updatePriority(providerCodes, principal.getName(), null);
            if (savedValue.equals(previousValue)) {
                return "제공자 우선순위가 그대로예요";
            }
            adminAuditService.record(principal.getName(), AdminAuditAction.CONFIG_UPDATE,
                    ConfigKey.AI_PROVIDER_PRIORITY.name(), previousValue + " -> " + savedValue);
            return "제공자 우선순위를 저장했어요";
        });
    }

    private AdminAiKeyProviderSection toProviderSection(KeyedAiProvider provider, boolean canStoreSecret) {
        AiApiKeyService keyService = findKeyService(provider);
        List<AiApiKeyView> storedKeyViews = keyService.findStoredKeyViews();
        return new AdminAiKeyProviderSection(provider, aiProviderModelService.findModel(provider), storedKeyViews,
                keyService.findDeployPropertyKeyViews(), keyService.isUsingDeployPropertyKeys(),
                canStoreSecret && storedKeyViews.size() < AiApiKeyPolicy.MAXIMUM_KEY_COUNT);
    }

    private AiApiKeyService findKeyService(KeyedAiProvider provider) {
        return aiApiKeyServiceRegistry.find(provider);
    }

    // 감사 기록 1건: 대상 = 키 id, 내용 = "제공자 · 별명" + 동작 덧말 (키 값은 넣지 않음)
    private void recordAudit(Principal principal, AdminAuditAction action, KeyedAiProvider provider,
                             AiApiKeyChange change, String actionSuffix) {
        adminAuditService.record(principal.getName(), action, change.keyId(),
                provider.getDisplayName() + " · " + change.nickname() + actionSuffix);
    }

    // 안내 문구의 키 이름 (예: OpenAI 운영)
    private static String toKeyLabel(KeyedAiProvider provider, AiApiKeyChange change) {
        return provider.getDisplayName() + " " + change.nickname();
    }

    // 키 변경 1건 공통: 제공자 코드 확인 > 실행 > 그 제공자 구역으로 돌아감 (모르는 코드는 AI_PROVIDER_UNKNOWN)
    private String changeKeys(String providerCode, RedirectAttributes redirectAttributes,
                              Function<KeyedAiProvider, String> change) {
        Optional<KeyedAiProvider> provider = KeyedAiProvider.fromProviderCode(providerCode);
        String redirectTarget = provider.map(knownProvider -> REDIRECT_TO_AI_KEYS + "#" + knownProvider.getProviderCode())
                .orElse(REDIRECT_TO_AI_KEYS);
        return applyChange(redirectAttributes, redirectTarget, () -> change.apply(
                provider.orElseThrow(() -> new CustomException(ErrorCode.AI_PROVIDER_UNKNOWN))));
    }

    // 변경 1건 공통: 실행 > 성공 문구 또는 거부 사유(문구 + 에러 코드) > 화면으로 돌아감
    private String applyChange(RedirectAttributes redirectAttributes, String redirectTarget, Supplier<String> change) {
        try {
            AdminFlash.addMessage(redirectAttributes, change.get());
        } catch (CustomException exception) {
            log.warn("[admin ai key] rejected {} : code={}", exception.getErrorCode().getStatus().value(),
                    exception.getErrorCode().name());
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return redirectTarget;
    }
}
