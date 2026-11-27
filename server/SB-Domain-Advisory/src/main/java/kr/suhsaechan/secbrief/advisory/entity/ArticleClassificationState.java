package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassification;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사 분류 상태 (Article 에 포함): 분야·생태계·제품·패키지·근거, AI 가 고른 분야, 주요 여부, 분류 시각
 * 열 이름은 article 테이블 그대로 (field_code, ecosystem_code, product_code, package_name, ai_field_code, field_source, is_notable, classified_at)
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleClassificationState {

    // 분류 (catalog_tag code, 없으면 null): 기술 분야, 오픈소스 생태계, 제품
    @Column(name = "field_code", length = 64)
    private String fieldCode;

    @Column(name = "ecosystem_code", length = 64)
    private String ecosystemCode;

    @Column(name = "product_code", length = 64)
    private String productCode;

    // GitHub 권고 첫 패키지 이름 (원문 그대로)
    @Column(name = "package_name", length = 200)
    private String packageName;

    // AI 가 제목 분석에서 고른 분야 (규칙으로 못 정할 때만 씀)
    @Column(name = "ai_field_code", length = 64)
    private String aiFieldCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_source", length = 16)
    private ClassificationSource fieldSource;

    // 주요 (ArticleNotablePolicy). NOT NULL 열이라 미분류 행도 이 객체가 null 로 읽히지 않음
    @Column(name = "is_notable", nullable = false)
    private boolean isNotable;

    // 분류한 시각 (null: 분류 대기)
    @Column(name = "classified_at")
    private Instant classifiedAt;

    /** 새 기사: 분류 값 없음, 주요 아님, 분류 대기 */
    public static ArticleClassificationState unclassified() {
        return new ArticleClassificationState();
    }

    /** 분류 결과 반영: 분야·생태계·제품·패키지·근거 + 주요 + 분류 시각 */
    void apply(ArticleClassification classification, boolean isNotable, Instant classifiedAt) {
        this.fieldCode = classification.fieldCode();
        this.ecosystemCode = classification.ecosystemCode();
        this.productCode = classification.productCode();
        this.packageName = classification.packageName();
        this.fieldSource = classification.fieldSource();
        this.isNotable = isNotable;
        this.classifiedAt = classifiedAt;
    }

    // KEV 등재 > 주요
    void markNotable() {
        this.isNotable = true;
    }

    void changeAiFieldCode(String aiFieldCode) {
        this.aiFieldCode = aiFieldCode;
    }

    // 분류 대기로 (분류 값은 다음 분류가 덮어씀)
    void requestReclassification() {
        this.classifiedAt = null;
    }

    // 제목이 바뀜 > AI 분야도 비우고 분류 대기로
    void resetForNewTitle() {
        this.aiFieldCode = null;
        this.classifiedAt = null;
    }
}
