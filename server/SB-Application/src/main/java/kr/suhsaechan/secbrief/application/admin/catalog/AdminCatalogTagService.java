package kr.suhsaechan.secbrief.application.admin.catalog;

import java.util.List;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTag;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTagContent;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogTagRepository;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassificationService;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분류 태그 관리 (관리자 화면): 종류별 목록, 추가, 고치기(code·종류는 그대로), 보이기·숨기기, 기사 전체 다시 분류
 * 바꾸면 스냅샷을 바로 버려 분류·API·관심 판정에 다음 조회부터 반영 (배포 없음)
 */
@Service
@RequiredArgsConstructor
public class AdminCatalogTagService {

    private static final Pattern CODE_PATTERN = Pattern.compile(CatalogTag.CODE_PATTERN);

    private final CatalogTagRepository catalogTagRepository;
    private final TagCatalogProvider tagCatalogProvider;
    private final ArticleClassificationService articleClassificationService;

    /** 종류별 목록 (숨긴 항목 포함, 순서대로) */
    @Transactional(readOnly = true)
    public List<CatalogTag> findAllByKind(CatalogTagKind kind) {
        return catalogTagRepository.findAllByOrderByKindAscDisplayOrderAscCodeAsc().stream()
                .filter(catalogTag -> catalogTag.getKind() == kind)
                .toList();
    }

    /** 고를 수 있는 묶음·분야 (참조 검증·화면 선택지) */
    @Transactional(readOnly = true)
    public List<CatalogTag> findReferenceOptions(CatalogTagKind kind) {
        return findAllByKind(kind);
    }

    /** 추가: code 모양·중복 > 이름 길이 > 묶음·분야 참조 > 저장 */
    @Transactional
    public CatalogTag create(CatalogTagKind kind, String code, CatalogTagContent content) {
        String normalizedCode = code == null ? "" : code.strip();
        if (!CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new CustomException(ErrorCode.CATALOG_TAG_CODE_INVALID);
        }
        if (catalogTagRepository.findByKindAndCode(kind, normalizedCode).isPresent()) {
            throw new CustomException(ErrorCode.CATALOG_TAG_CODE_DUPLICATED);
        }
        CatalogTagContent validContent = toValidContent(kind, content);
        CatalogTag catalogTag = catalogTagRepository.save(CatalogTag.create(kind, normalizedCode, validContent));
        tagCatalogProvider.invalidate();
        return catalogTag;
    }

    /** 고치기 (이름·묶음·분야·낱말·널리 쓰는지·순서) */
    @Transactional
    public CatalogTag update(long catalogTagId, CatalogTagContent content) {
        CatalogTag catalogTag = findById(catalogTagId);
        catalogTag.update(toValidContent(catalogTag.getKind(), content));
        tagCatalogProvider.invalidate();
        return catalogTag;
    }

    /** 보이기·숨기기 (숨기면 관심 기술에 남은 code 는 조용히 무시됨) */
    @Transactional
    public CatalogTag changeVisible(long catalogTagId, boolean isVisible) {
        CatalogTag catalogTag = findById(catalogTagId);
        catalogTag.changeVisible(isVisible);
        tagCatalogProvider.invalidate();
        return catalogTag;
    }

    /** 기사 전체 다시 분류 (분류 틱이 차례로, 알림은 다시 보내지 않음) */
    public int requestReclassificationOfAll() {
        return articleClassificationService.requestReclassificationOfAll();
    }

    private CatalogTag findById(long catalogTagId) {
        return catalogTagRepository.findById(catalogTagId)
                .orElseThrow(() -> new CustomException(ErrorCode.CATALOG_TAG_NOT_FOUND));
    }

    // 이름 공백 정리·길이, PRODUCT 만 묶음·분야 (있는 code 만), 널리 쓰는지는 PRODUCT 만
    private CatalogTagContent toValidContent(CatalogTagKind kind, CatalogTagContent content) {
        String name = content.name() == null ? "" : content.name().strip();
        String shortName = content.shortName() == null || content.shortName().isBlank() ? name : content.shortName().strip();
        if (name.isEmpty() || name.length() > CatalogTag.MAXIMUM_NAME_LENGTH
                || shortName.length() > CatalogTag.MAXIMUM_SHORT_NAME_LENGTH) {
            throw new CustomException(ErrorCode.CATALOG_TAG_NAME_INVALID);
        }
        boolean isProduct = kind == CatalogTagKind.PRODUCT;
        String groupCode = isProduct ? toReference(CatalogTagKind.PRODUCT_GROUP, content.groupCode()) : null;
        String fieldCode = isProduct ? toReference(CatalogTagKind.FIELD, content.fieldCode()) : null;
        String matchTerms = content.matchTerms() == null ? "" : content.matchTerms().strip();
        return new CatalogTagContent(name, shortName, groupCode, fieldCode, matchTerms,
                isProduct && content.isWidelyUsed(), content.displayOrder());
    }

    // 빈 값은 null, 값이 있으면 그 종류에 있는 code 여야 함
    private String toReference(CatalogTagKind referenceKind, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return catalogTagRepository.findByKindAndCode(referenceKind, code.strip())
                .map(CatalogTag::getCode)
                .orElseThrow(() -> new CustomException(ErrorCode.CATALOG_TAG_REFERENCE_INVALID));
    }
}
