package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사 AI 제목 분석 상태 (Article 에 포함): 주제·번역 제목·회사·제품 이름·키워드, 처리 상태·시도 횟수
 * 열 이름은 article 테이블 그대로 (topic, translated_title, subject_name, ai_status, ai_attempt_count + article_keyword), 중요도는 규칙 하한과 묶여 Article 에 둠
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleAiAnalysisState {

    // AI 최대 시도 횟수 (넘으면 FAILED, 다시 부르지 않음)
    public static final int MAXIMUM_ATTEMPT_COUNT = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic", nullable = false, length = 32)
    private ArticleTopic topic;

    // AI 한국어 제목: 해외 항목이고 검증 통과한 경우만
    @Column(name = "translated_title", length = 500)
    private String translatedTitle;

    // 제목에 나온 회사·제품 이름: AI 가 뽑고 제목에 그대로 있는 글자만 받음 (없으면 null)
    @Column(name = "subject_name", length = 80)
    private String subjectName;

    // AI 가 제목에서 뽑은 키워드 (검증 통과분, 최대 5개, 표기 그대로)
    // 즉시 로딩: 응답 변환이 트랜잭션 밖에서도 읽음 (목록은 default_batch_fetch_size 로 묶어 조회)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_keyword", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "keyword", nullable = false, length = 40)
    private Set<String> keywords = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 16)
    private AiStatus status;

    @Column(name = "ai_attempt_count", nullable = false)
    private int attemptCount;

    /** 새 기사·제목 교체: 주제 GENERAL, 번역·이름·키워드 없음, 대기(PENDING), 시도 0회 */
    static ArticleAiAnalysisState pending() {
        ArticleAiAnalysisState aiAnalysisState = new ArticleAiAnalysisState();
        aiAnalysisState.restart();
        return aiAnalysisState;
    }

    /** 처음부터 다시 대기 (제목이 바뀜): 결과 비움 > PENDING, 시도 0회 */
    void restart() {
        clearResult();
        this.status = AiStatus.PENDING;
        this.attemptCount = 0;
    }

    /** AI 결과 반영: 주제 + 검증 통과한 번역·회사·제품 이름(없으면 null) > DONE (키워드는 replaceKeywords) */
    void apply(ArticleTopic topic, String acceptedTranslatedTitle, String acceptedSubjectName) {
        this.topic = topic;
        this.translatedTitle = acceptedTranslatedTitle;
        this.subjectName = acceptedSubjectName;
        this.status = AiStatus.DONE;
    }

    /** 실패 기록: 결과 비움, 시도 3회째면 FAILED 아니면 PENDING 유지 */
    void recordFailure() {
        clearResult();
        this.attemptCount++;
        this.status = attemptCount >= MAXIMUM_ATTEMPT_COUNT ? AiStatus.FAILED : AiStatus.PENDING;
    }

    /** 분야만 다시 고르게 대기로 (결과는 유지, 시도 0회) */
    void requestReanalysis() {
        this.status = AiStatus.PENDING;
        this.attemptCount = 0;
    }

    /** 키워드 교체: 검증 통과한 목록으로 통째로 바꿈 */
    void replaceKeywords(Collection<String> acceptedKeywords) {
        this.keywords.clear();
        this.keywords.addAll(acceptedKeywords);
    }

    /** 키워드 목록 (대소문자 무시 글자순: 저장 순서를 보관하지 않아 응답·임베딩 입력이 매번 같게) */
    public List<String> getKeywords() {
        return keywords.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    // 결과 4개 비움 (주제 GENERAL, 번역·이름 null, 키워드 없음)
    private void clearResult() {
        this.topic = ArticleTopic.GENERAL;
        this.translatedTitle = null;
        this.subjectName = null;
        this.keywords.clear();
    }
}
