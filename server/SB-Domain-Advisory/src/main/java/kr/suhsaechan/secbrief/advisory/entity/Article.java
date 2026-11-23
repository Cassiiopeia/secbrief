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
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집한 기사 1건: 제목, 원문 링크, 게시자, 게시일, 주제, AI 처리 상태 (본문은 저장 안 함)
 * 생성 > CVE 병합(KEV 표시, NVD 링크로 교체) > AI 결과 반영 순으로 상태가 바뀜
 */
@Entity
@Table(name = "article")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity {

    // AI 최대 시도 횟수 (넘으면 FAILED, 다시 부르지 않음)
    public static final int MAXIMUM_AI_ATTEMPT_COUNT = 3;

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

    /**
     * 새 기사: 주제 GENERAL, AI 대기(PENDING), 번역 없음
     */
    public static Article create(Source source, ArticleDraft draft, Instant collectedAt) {
        Article article = new Article();
        article.source = source;
        article.kind = draft.kind();
        article.region = draft.region();
        article.originalUrl = draft.originalUrl();
        article.cveId = draft.cveId();
        article.isKev = draft.isKev();
        article.isUrgent = draft.isUrgent();
        article.collectedAt = collectedAt;
        article.applyLinkFields(draft);
        article.resetAiState();
        return article;
    }

    /**
     * KEV 등재 표시 (등재는 곧 긴급)
     */
    public void markKev() {
        this.isKev = true;
        this.isUrgent = true;
    }

    /**
     * 긴급 표시 (병합 시 기존 || 새 값)
     */
    public void markUrgent() {
        this.isUrgent = true;
    }

    /**
     * KEV·GHSA 행 > NVD 링크·게시자·제목·점수로 교체 (NVD 가 링크 원본)
     * 제목이 바뀌므로 AI 결과를 비우고 다시 대기 (KEV 표시·긴급은 유지)
     */
    public void replaceLinkWithNvd(Source nvdSource, ArticleDraft nvdDraft) {
        this.source = nvdSource;
        this.originalUrl = nvdDraft.originalUrl();
        this.isUrgent = this.isUrgent || nvdDraft.isUrgent();
        applyLinkFields(nvdDraft);
        resetAiState();
    }

    /**
     * AI 결과 반영: 주제 + 검증 통과한 번역(없으면 null) > DONE
     */
    public void applyAiAnalysis(ArticleTopic topic, String acceptedTranslatedTitle) {
        this.topic = topic;
        this.translatedTitle = acceptedTranslatedTitle;
        this.aiStatus = AiStatus.DONE;
    }

    /**
     * AI 실패 기록: 주제 GENERAL·번역 없음, 시도 3회째면 FAILED 아니면 PENDING 유지
     */
    public void recordAiFailure() {
        this.topic = ArticleTopic.GENERAL;
        this.translatedTitle = null;
        this.aiAttemptCount++;
        this.aiStatus = aiAttemptCount >= MAXIMUM_AI_ATTEMPT_COUNT ? AiStatus.FAILED : AiStatus.PENDING;
    }

    // 출처마다 다른 표시 값 (제목, 게시자, 게시일, 점수)
    private void applyLinkFields(ArticleDraft draft) {
        this.title = draft.title();
        this.publisherName = draft.publisherName();
        this.publishedAt = draft.publishedAt();
        this.isPublishedAtEstimated = draft.isPublishedAtEstimated();
        this.cvssScore = draft.cvssScore();
        this.cvssVersion = draft.cvssVersion();
    }

    private void resetAiState() {
        this.topic = ArticleTopic.GENERAL;
        this.translatedTitle = null;
        this.aiStatus = AiStatus.PENDING;
        this.aiAttemptCount = 0;
    }
}
