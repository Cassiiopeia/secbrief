package kr.suhsaechan.secbrief.advisory.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 분류 태그 1개: 종류·바뀌지 않는 code·이름·묶음·분야·분류 낱말·널리 쓰는지·보이는지·순서
 * 기사 태그·기기 관심 기술은 code 만 저장 (이름을 바꿔도 연결 유지)
 */
@Entity
@Table(name = "catalog_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogTag extends BaseEntity {

    // code 모양: 소문자·숫자·- (1~64자, 처음은 소문자·숫자)
    public static final String CODE_PATTERN = "^[a-z0-9][a-z0-9-]{0,63}$";
    public static final int MAXIMUM_NAME_LENGTH = 80;
    public static final int MAXIMUM_SHORT_NAME_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CatalogTagKind kind;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = MAXIMUM_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = MAXIMUM_SHORT_NAME_LENGTH)
    private String shortName;

    // PRODUCT 의 인기 묶음 code (없으면 검색으로만)
    @Column(length = 64)
    private String groupCode;

    // PRODUCT 의 기술 분야 code
    @Column(length = 64)
    private String fieldCode;

    // 분류 낱말: 쉼표 구분, ~ 로 시작하면 약한 낱말 (CatalogTermMatcher)
    @Column(nullable = false, columnDefinition = "text")
    private String matchTerms;

    @Column(nullable = false)
    private boolean isWidelyUsed;

    @Column(nullable = false)
    private boolean isVisible;

    @Column(nullable = false)
    private int displayOrder;

    /** 새 항목 (보이는 상태) */
    public static CatalogTag create(CatalogTagKind kind, String code, CatalogTagContent content) {
        CatalogTag catalogTag = new CatalogTag();
        catalogTag.kind = kind;
        catalogTag.code = code;
        catalogTag.isVisible = true;
        catalogTag.update(content);
        return catalogTag;
    }

    /** 내용 교체 (code·종류는 바뀌지 않음) */
    public void update(CatalogTagContent content) {
        this.name = content.name();
        this.shortName = content.shortName();
        this.groupCode = content.groupCode();
        this.fieldCode = content.fieldCode();
        this.matchTerms = content.matchTerms() == null ? "" : content.matchTerms();
        this.isWidelyUsed = content.isWidelyUsed();
        this.displayOrder = content.displayOrder();
    }

    public void changeVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }
}
