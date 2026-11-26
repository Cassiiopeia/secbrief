package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactJson;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactPolicy;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ArticleImportancePolicy;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 수집한 기사 1건: 제목, 원문 링크, 게시자, 게시일, 주제·중요도·키워드, AI 처리 상태, 대표 이미지·요약 상태, 사실 값 (본문은 저장 안 함)
 * 생성 > CVE 병합(KEV 표시, NVD 링크로 교체) > AI 결과 반영 순으로 상태가 바뀜, 대표 이미지·요약은 따로 대기 > 저장
 */
@Entity
@Table(name = "article")
// 바뀐 열만 UPDATE: 수집 병합·AI 기록·알림 표시가 같은 행을 따로 고쳐도 서로의 열을 덮지 않게
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article extends BaseEntity {

    // AI 최대 시도 횟수 (넘으면 FAILED, 다시 부르지 않음)
    public static final int MAXIMUM_AI_ATTEMPT_COUNT = 3;
    // 새로 수집한 긴급 소식을 알리는 게시일 범위 (3일보다 오래된 소식은 알리지 않음, 뒤늦은 KEV 등재는 따로 알림)
    public static final Duration URGENT_ALERT_FRESH_DURATION = Duration.ofDays(3);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 즉시 로딩: 중요도 하한·응답의 출처 코드가 트랜잭션 밖에서도 출처를 읽음 (목록은 default_batch_fetch_size 로 묶어 조회)
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
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

    // 중요도: AI 판단과 규칙 하한(ArticleImportancePolicy) 중 높은 쪽
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ArticleImportance importance;

    // 제목에 나온 회사·제품 이름: AI 가 뽑고 제목에 그대로 있는 글자만 받음 (없으면 null)
    @Column(length = 80)
    private String subjectName;

    // AI 가 제목에서 뽑은 키워드 (검증 통과분, 최대 5개, 표기 그대로)
    // 즉시 로딩: 응답 변환이 트랜잭션 밖에서도 읽음 (목록은 default_batch_fetch_size 로 묶어 조회)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_keyword", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "keyword", nullable = false, length = 40)
    private Set<String> keywords = new LinkedHashSet<>();

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

    // 긴급: CVSS 9.0 이상, KEV 등재, KISA 공지 제목에 '긴급', KISA 경보단계 '상향' (ArticleUrgencyPolicy)
    @Column(nullable = false)
    private boolean isUrgent;

    @Column(nullable = false)
    private Instant collectedAt;

    // 긴급 알림 발송 처리 시각 (null: 알림 대기, 받을 기기가 없어도 처리하면 채움)
    private Instant urgentNotifiedAt;

    // 대표 이미지 주소·저장 상태
    @Embedded
    private ArticleImageState imageState;

    // 원문 데이터의 사실 값 (JSON 배열 [{label, value}], 없으면 null): 수집기가 채우고 AI 를 거치지 않음
    @Column(name = "source_facts", columnDefinition = "text")
    private String sourceFactsJson;

    // AI 요약 입력·결과·상태 (앱 안 상세)
    @Embedded
    private ArticleSummaryState summaryState;

    /** 새 기사: 주제 GENERAL, 중요도 규칙 하한, AI 대기(PENDING), 번역 없음, 이미지 주소·요약 입력이 있으면 각각 대기(PENDING) 없으면 NONE */
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
        article.imageState = ArticleImageState.ofSourceUrl(draft.imageSourceUrl());
        // 게시된 지 오래된 긴급 소식은 처음 수집해도 알리지 않음 (첫 수집 분량·예전 경보단계 공지)
        if (article.isUrgent && article.publishedAt.isBefore(collectedAt.minus(URGENT_ALERT_FRESH_DURATION))) {
            article.urgentNotifiedAt = collectedAt;
        }
        article.sourceFactsJson = SourceFactJson.toJson(draft.sourceFacts());
        article.summaryState = ArticleSummaryState.ofSourceText(draft.sourceText());
        return article;
    }

    /**
     * KEV 등재 표시 (등재는 곧 긴급)
     * 고위험으로 이미 알린 취약점도 악용 확인은 새 소식이라 알림 대기로 되돌림
     */
    public void markKev() {
        this.isKev = true;
        this.isUrgent = true;
        this.urgentNotifiedAt = null;
        this.importance = ArticleImportance.higherOf(this.importance, toImportanceFloor());
    }

    /** 긴급 표시 (병합 시 기존 || 새 값) */
    public void markUrgent() {
        this.isUrgent = true;
    }

    /**
     * KEV·GHSA 행 > NVD 링크·게시자·제목·점수로 교체 (NVD 가 링크 원본)
     * 제목이 바뀌므로 AI 결과를 비우고 다시 대기 (KEV 표시·긴급·대표 이미지는 유지)
     */
    public void replaceLinkWithNvd(Source nvdSource, ArticleDraft nvdDraft) {
        this.source = nvdSource;
        this.originalUrl = nvdDraft.originalUrl();
        this.isUrgent = this.isUrgent || nvdDraft.isUrgent();
        applyLinkFields(nvdDraft);
        resetAiState();
        // 사실 값: NVD 가 준 라벨만 NVD 값으로 (KEV·GHSA 고유 값 유지), 요약: NVD 설명이 있으면 그 글로 다시 대기
        this.sourceFactsJson = SourceFactJson.toJson(
                SourceFactPolicy.mergeReplacingLabels(getSourceFacts(), nvdDraft.sourceFacts()));
        if (nvdDraft.sourceText() != null) {
            summaryState.restart(nvdDraft.sourceText());
        }
    }

    /**
     * 이미 있는 기사에 다시 수집한 값 받기 (같은 CVE 병합, 같은 링크 재수집): 사실 값은 없는 라벨만 추가,
     * 요약 입력·이미지 주소는 없던(NONE) 행만 받아 대기로. 반환: 바뀐 것이 있는지
     */
    public boolean mergeSourceDetails(ArticleDraft draft) {
        String mergedSourceFactsJson = SourceFactJson.toJson(
                SourceFactPolicy.mergeKeepingExisting(getSourceFacts(), draft.sourceFacts()));
        boolean hasNewFacts = !Objects.equals(sourceFactsJson, mergedSourceFactsJson);
        boolean hasNewSourceText = summaryState.getStatus() == SummaryStatus.NONE && draft.sourceText() != null;
        this.sourceFactsJson = mergedSourceFactsJson;
        if (hasNewSourceText) {
            summaryState.restart(draft.sourceText());
        }
        boolean hasNewImageSourceUrl = imageState.attachSourceUrlIfNone(draft.imageSourceUrl());
        return hasNewFacts || hasNewSourceText || hasNewImageSourceUrl;
    }

    public List<SourceFact> getSourceFacts() {
        return SourceFactJson.toSourceFacts(sourceFactsJson);
    }

    /** AI 결과 반영: 주제 + 검증 통과한 번역·회사·제품 이름(없으면 null) + 중요도(규칙 하한보다 낮으면 하한) > DONE */
    public void applyAiAnalysis(ArticleTopic topic, String acceptedTranslatedTitle,
                                ArticleImportance aiImportance, String acceptedSubjectName) {
        this.topic = topic;
        this.translatedTitle = acceptedTranslatedTitle;
        this.importance = ArticleImportance.higherOf(aiImportance, toImportanceFloor());
        this.subjectName = acceptedSubjectName;
        this.aiStatus = AiStatus.DONE;
    }

    /** 키워드 교체: 검증 통과한 목록으로 통째로 바꿈 (AI 결과 반영 때) */
    public void replaceKeywords(Collection<String> acceptedKeywords) {
        this.keywords.clear();
        this.keywords.addAll(acceptedKeywords);
    }

    /** 키워드 목록 (대소문자 무시 글자순: 저장 순서를 보관하지 않아 응답·임베딩 입력이 매번 같게) */
    public List<String> getKeywords() {
        return keywords.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /** AI 실패 기록: 주제 GENERAL·번역·키워드 없음·중요도 규칙 하한, 시도 3회째면 FAILED 아니면 PENDING 유지 */
    public void recordAiFailure() {
        this.topic = ArticleTopic.GENERAL;
        this.translatedTitle = null;
        this.importance = toImportanceFloor();
        this.subjectName = null;
        this.keywords.clear();
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

    // 중요도 규칙 하한 (KEV 등재·KISA 긴급·경보 상향): AI 판단 전 값이자 AI 결과의 최소 단계
    private ArticleImportance toImportanceFloor() {
        return ArticleImportancePolicy.toImportanceFloor(source.getCode(), title, isKev);
    }

    private void resetAiState() {
        this.topic = ArticleTopic.GENERAL;
        this.translatedTitle = null;
        this.importance = toImportanceFloor();
        this.subjectName = null;
        this.keywords.clear();
        this.aiStatus = AiStatus.PENDING;
        this.aiAttemptCount = 0;
    }
}
