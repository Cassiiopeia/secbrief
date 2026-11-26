package kr.suhsaechan.secbrief.web.admin.controller;

import java.security.Principal;
import java.util.Arrays;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTag;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTagContent;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.application.admin.catalog.AdminCatalogTagService;
import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
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
 * 분류 태그 관리 (#93): 종류 탭별 표, 추가, 고치기, 보이기·숨기기, 기사 전체 다시 분류 (바꾼 내용은 감사 기록)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminCatalogTagController {

    private static final String CATALOG_PATH = "/admin/catalog-tags";
    private static final String CATALOG_VIEW = "admin/catalog-tags";

    private final AdminCatalogTagService adminCatalogTagService;
    private final AdminAuditService adminAuditService;

    @GetMapping(CATALOG_PATH)
    public String showCatalogTags(@RequestParam(defaultValue = "FIELD") CatalogTagKind kind, Model model) {
        model.addAttribute("selectedKind", kind);
        model.addAttribute("kindTabs", Arrays.stream(CatalogTagKind.values())
                .map(tabKind -> new KindTab(tabKind, AdminDisplayLabels.ofCatalogTagKind(tabKind)))
                .toList());
        model.addAttribute("selectedKindLabel", AdminDisplayLabels.ofCatalogTagKind(kind));
        model.addAttribute("catalogTags", adminCatalogTagService.findAllByKind(kind));
        model.addAttribute("groupOptions", adminCatalogTagService.findReferenceOptions(CatalogTagKind.PRODUCT_GROUP));
        model.addAttribute("fieldOptions", adminCatalogTagService.findReferenceOptions(CatalogTagKind.FIELD));
        return CATALOG_VIEW;
    }

    /** 추가: code 는 처음 한 번만 정함 (기사·기기가 code 로 연결) */
    @PostMapping(CATALOG_PATH)
    public String createCatalogTag(@RequestParam CatalogTagKind kind, @RequestParam(required = false) String code,
                                   CatalogTagForm form, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            CatalogTag catalogTag = adminCatalogTagService.create(kind, code, form.toContent());
            recordAudit(principal, AdminAuditAction.CATALOG_TAG_CREATE, catalogTag, "name=" + catalogTag.getName());
            AdminFlash.addMessage(redirectAttributes, "「" + catalogTag.getName() + "」 항목을 추가했어요");
        } catch (CustomException exception) {
            rejectWith(exception, redirectAttributes, "create");
        }
        return redirectTo(kind);
    }

    @PostMapping(CATALOG_PATH + "/{catalogTagId}")
    public String updateCatalogTag(@PathVariable long catalogTagId, @RequestParam CatalogTagKind kind,
                                   CatalogTagForm form, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            CatalogTag catalogTag = adminCatalogTagService.update(catalogTagId, form.toContent());
            recordAudit(principal, AdminAuditAction.CATALOG_TAG_UPDATE, catalogTag, "name=" + catalogTag.getName());
            AdminFlash.addMessage(redirectAttributes, "「" + catalogTag.getName() + "」 항목을 저장했어요");
        } catch (CustomException exception) {
            rejectWith(exception, redirectAttributes, "update");
        }
        return redirectTo(kind);
    }

    @PostMapping(CATALOG_PATH + "/{catalogTagId}/visibility")
    public String changeVisibility(@PathVariable long catalogTagId, @RequestParam CatalogTagKind kind,
                                   @RequestParam(name = "visible", defaultValue = "false") boolean isVisible,
                                   Principal principal, RedirectAttributes redirectAttributes) {
        try {
            CatalogTag catalogTag = adminCatalogTagService.changeVisible(catalogTagId, isVisible);
            recordAudit(principal, AdminAuditAction.CATALOG_TAG_VISIBILITY, catalogTag, "visible=" + isVisible);
            AdminFlash.addMessage(redirectAttributes,
                    "「" + catalogTag.getName() + "」 항목을 " + (isVisible ? "다시 보여요" : "숨겼어요"));
        } catch (CustomException exception) {
            rejectWith(exception, redirectAttributes, "visibility");
        }
        return redirectTo(kind);
    }

    /** 기사 전체 다시 분류 (새 항목을 기존 기사에도 붙임, 같은 요청을 여러 번 해도 결과는 같음) */
    @PostMapping(CATALOG_PATH + "/reclassify")
    public String reclassifyAll(@RequestParam CatalogTagKind kind, Principal principal,
                                RedirectAttributes redirectAttributes) {
        int requestedCount = adminCatalogTagService.requestReclassificationOfAll();
        adminAuditService.record(principal.getName(), AdminAuditAction.CATALOG_RECLASSIFY, "ALL",
                "articles=" + requestedCount);
        AdminFlash.addMessage(redirectAttributes, "기사 " + requestedCount + "건을 차례로 다시 분류해요");
        return redirectTo(kind);
    }

    private void recordAudit(Principal principal, AdminAuditAction action, CatalogTag catalogTag, String detail) {
        adminAuditService.record(principal.getName(), action, catalogTag.getKind() + ":" + catalogTag.getCode(), detail);
        log.info("[admin catalog] {} : tag={}:{}", action.name().toLowerCase(), catalogTag.getKind(), catalogTag.getCode());
    }

    private static void rejectWith(CustomException exception, RedirectAttributes redirectAttributes, String operation) {
        log.warn("[admin catalog] rejected {} : code={}, operation={}", exception.getErrorCode().getStatus().value(),
                exception.getErrorCode().name(), operation);
        AdminFlash.addError(redirectAttributes, exception.getErrorCode());
    }

    private static String redirectTo(CatalogTagKind kind) {
        return "redirect:" + CATALOG_PATH + "?kind=" + kind.name();
    }

    /** 종류 탭 1개: 종류 + 표시 이름 */
    public record KindTab(CatalogTagKind kind, String label) {
    }

    /** 화면 입력: 이름, 짧은 이름, 묶음, 분야, 분류 낱말, 널리 쓰는지(꺼진 체크 상자는 값이 오지 않음), 순서 */
    public record CatalogTagForm(String name, String shortName, String groupCode, String fieldCode, String matchTerms,
                                 Boolean widelyUsed, Integer displayOrder) {

        CatalogTagContent toContent() {
            return new CatalogTagContent(name, shortName, groupCode, fieldCode, matchTerms,
                    Boolean.TRUE.equals(widelyUsed), displayOrder == null ? 0 : displayOrder);
        }
    }
}
