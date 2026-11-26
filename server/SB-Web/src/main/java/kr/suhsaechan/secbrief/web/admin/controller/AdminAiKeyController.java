package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;
import kr.suhsaechan.secbrief.advisory.ai.AiProviderPriorityService;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyChange;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyPolicy;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyView;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
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
 * AI 키 화면: Anthropic 키 표(가린 값·상태), 추가·삭제·위아래 이동·켜기·끄기·값 교체·별명 변경, 제공자 우선순위 (전부 폼 POST > 리다이렉트)
 * 키 값은 받기만 하고 내려보내지 않음. 감사 기록에는 키 id 와 별명만 남김
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminAiKeyController {

    private static final String AI_KEYS_PATH = "/admin/ai-keys";
    private static final String AI_KEYS_VIEW = "admin/ai-keys";
    private static final String REDIRECT_TO_AI_KEYS = "redirect:" + AI_KEYS_PATH;
    // move 요청의 direction 값: up 위로, 그 외 아래로
    private static final String DIRECTION_UP = "up";

    private final AnthropicApiKeyService anthropicApiKeyService;
    private final AiProviderPriorityService aiProviderPriorityService;
    private final SystemConfigService systemConfigService;
    private final AdminAuditService adminAuditService;

    @GetMapping(AI_KEYS_PATH)
    public String showAiKeys(Model model) {
        List<AnthropicApiKeyView> storedKeyViews = anthropicApiKeyService.findStoredKeyViews();
        boolean canStoreSecret = systemConfigService.canStoreSecret();
        model.addAttribute("storedKeyViews", storedKeyViews);
        model.addAttribute("deployPropertyKeyViews", anthropicApiKeyService.findDeployPropertyKeyViews());
        model.addAttribute("isUsingDeployPropertyKeys", anthropicApiKeyService.isUsingDeployPropertyKeys());
        model.addAttribute("canStoreSecret", canStoreSecret);
        model.addAttribute("canAddKey", canStoreSecret && storedKeyViews.size() < AnthropicApiKeyPolicy.MAXIMUM_KEY_COUNT);
        model.addAttribute("maximumKeyCount", AnthropicApiKeyPolicy.MAXIMUM_KEY_COUNT);
        model.addAttribute("maximumNicknameLength", AnthropicApiKeyPolicy.MAXIMUM_NICKNAME_LENGTH);
        model.addAttribute("minimumApiKeyLength", AnthropicApiKeyPolicy.MINIMUM_API_KEY_LENGTH);
        model.addAttribute("maximumApiKeyLength", AnthropicApiKeyPolicy.MAXIMUM_API_KEY_LENGTH);
        model.addAttribute("providerViews", aiProviderPriorityService.findProviderViews());
        model.addAttribute("priorityCodes", aiProviderPriorityService.findPriorityCodes());
        return AI_KEYS_VIEW;
    }

    @PostMapping(AI_KEYS_PATH)
    public String addKey(@RequestParam(required = false) String nickname, @RequestParam(required = false) String apiKey,
                         Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.add(nickname, apiKey, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_ADD, change.keyId(), change.nickname());
            return change.nickname() + " 키를 추가했어요";
        });
    }

    @PostMapping(AI_KEYS_PATH + "/{keyId}/remove")
    public String removeKey(@PathVariable String keyId, Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.remove(keyId, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_REMOVE, change.keyId(), change.nickname());
            return change.nickname() + " 키를 삭제했어요";
        });
    }

    @PostMapping(AI_KEYS_PATH + "/{keyId}/move")
    public String moveKey(@PathVariable String keyId, @RequestParam(required = false) String direction,
                          Principal principal, RedirectAttributes redirectAttributes) {
        boolean isMovingUp = DIRECTION_UP.equals(direction);
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.move(keyId, isMovingUp, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_MOVE, change.keyId(),
                    change.nickname() + (isMovingUp ? " 위로" : " 아래로"));
            return change.nickname() + " 키의 우선순위를 " + (isMovingUp ? "올렸어요" : "내렸어요");
        });
    }

    @PostMapping(AI_KEYS_PATH + "/{keyId}/toggle")
    public String toggleKey(@PathVariable String keyId,
                            @RequestParam(name = "enabled", defaultValue = "false") boolean isEnabled,
                            Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.setEnabled(keyId, isEnabled, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_TOGGLE, change.keyId(),
                    change.nickname() + (isEnabled ? " 켬" : " 끔"));
            return change.nickname() + " 키를 " + (isEnabled ? "켰어요" : "껐어요");
        });
    }

    @PostMapping(AI_KEYS_PATH + "/{keyId}/replace")
    public String replaceKey(@PathVariable String keyId, @RequestParam(required = false) String apiKey,
                             Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.replaceApiKey(keyId, apiKey, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_REPLACE, change.keyId(), change.nickname());
            return change.nickname() + " 키 값을 바꿨어요";
        });
    }

    @PostMapping(AI_KEYS_PATH + "/{keyId}/rename")
    public String renameKey(@PathVariable String keyId, @RequestParam(required = false) String nickname,
                            Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
            AnthropicApiKeyChange change = anthropicApiKeyService.rename(keyId, nickname, principal.getName());
            adminAuditService.record(principal.getName(), AdminAuditAction.AI_KEY_RENAME, change.keyId(), change.nickname());
            return "별명을 " + change.nickname() + "(으)로 바꿨어요";
        });
    }

    /**
     * 제공자 우선순위 저장: providerCodes 순서 = 우선순위 (빈 칸은 버림)
     */
    @PostMapping(AI_KEYS_PATH + "/provider-priority")
    public String updateProviderPriority(@RequestParam(name = "providerCodes", required = false) List<String> providerCodes,
                                         Principal principal, RedirectAttributes redirectAttributes) {
        return changeKeys(redirectAttributes, () -> {
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

    // 변경 1건 공통: 실행 > 성공 문구 또는 거부 사유(문구 + 에러 코드) > 화면으로 돌아감
    private String changeKeys(RedirectAttributes redirectAttributes, Supplier<String> change) {
        try {
            AdminFlash.addMessage(redirectAttributes, change.get());
        } catch (CustomException exception) {
            log.warn("[admin ai key] rejected {} : code={}", exception.getErrorCode().getStatus().value(),
                    exception.getErrorCode().name());
            AdminFlash.addError(redirectAttributes, exception.getErrorCode());
        }
        return REDIRECT_TO_AI_KEYS;
    }
}
