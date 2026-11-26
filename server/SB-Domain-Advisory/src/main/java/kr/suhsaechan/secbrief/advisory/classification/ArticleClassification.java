package kr.suhsaechan.secbrief.advisory.classification;

import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;

/**
 * 분류 결과: 분야·생태계·제품 code (없으면 null), 첫 패키지 이름 원문, 분야 근거
 */
public record ArticleClassification(String fieldCode, String ecosystemCode, String productCode, String packageName,
                                    ClassificationSource fieldSource) {

    /** 분류하지 않는 항목 (KISA 경보단계·보고서) */
    public static ArticleClassification notClassified() {
        return new ArticleClassification(null, null, null, null, ClassificationSource.NONE);
    }
}
