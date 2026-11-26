package kr.suhsaechan.secbrief.advisory.repository;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;

/**
 * 오늘 새 소식 건수용 행 (본문·요약 없이 분류 값만): 출처 코드, 종류, KEV, 분야·생태계·제품 code, 패키지·제목 속 이름, 수집 시각
 */
public record NewArticleTagRow(String sourceCode, ArticleKind kind, boolean isKev, String fieldCode,
                               String ecosystemCode, String productCode, String packageName, String subjectName,
                               Instant collectedAt) {
}
