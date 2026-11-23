package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집한 기사 1건: 제목, 원문 링크, 게시자, 게시일, 주제, AI 처리 상태 (본문은 저장 안 함)
 * 생성·병합·AI 결과 반영 메서드는 해당 기능 구현 시 추가
 */
@Entity
@Table(name = "article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleKind kind;

    // 원문 제목 (정리 후 500자 이내)
    @Column(nullable = false, length = 500)
    private String title;

    // AI 한국어 제목: 해외 항목이고 검증 통과한 경우만
    @Column(length = 500)
    private String translatedTitle;

    @Column(nullable = false, unique = true, length = 1000)
    private String originalUrl;

    @Column(nullable = false, length = 100)
    private String publisherName;

    @Column(nullable = false)
    private Instant publishedAt;

    // 출처가 게시일을 안 주거나 미래 시각이라 수집 시각으로 대체함
    @Column(nullable = false)
    private boolean isPublishedAtEstimated;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleRegion region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArticleTopic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiStatus aiStatus;

    @Column(nullable = false)
    private int aiAttemptCount;

    // CVE 항목만 값 있음 (KEV·NVD·GHSA 병합 기준)
    @Column(unique = true, length = 32)
    private String cveId;

    // NVD 기본 점수 (v3.1 우선, 없으면 v4.0)
    @Column(precision = 3, scale = 1)
    private BigDecimal cvssScore;

    // 점수 버전 (예: 3.1, 4.0)
    @Column(length = 8)
    private String cvssVersion;

    // CISA KEV 등재 여부
    @Column(nullable = false)
    private boolean isKev;

    // 긴급: CVSS 9.0 이상, KEV 등재, KISA 공지 제목에 '긴급'
    @Column(nullable = false)
    private boolean isUrgent;

    @Column(nullable = false)
    private Instant collectedAt;
}
