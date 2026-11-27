package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBriefJson;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummary;
import kr.suhsaechan.secbrief.advisory.summary.SummaryKeyPointJson;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사의 AI 요약 상태 (article 표의 요약 열 11개): 요약 입력, 브리핑 5개, 이전 구조 3개(요약·핵심 정리·대응 안내), 상태, 시도 횟수
 * 입력 있음(PENDING) > 브리핑 반영(DONE) 또는 3회 실패(FAILED), 끝나면 요약 입력을 비움 (본문을 보관하지 않음)
 * 이전 구조 3개는 브리핑에서 옮긴 값 (ArticleBrief.toLegacySummary): 이전 앱 빌드와 배포 전환·되돌리기 중의 이전 서버 버전이 읽음
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleSummaryState {

    // 요약 최대 시도 횟수 (넘으면 FAILED, 다시 부르지 않음)
    public static final int MAXIMUM_ATTEMPT_COUNT = 3;

    // AI 요약 입력: 피드 요약문·공개 취약점 설명 (PENDING 동안만 값 있음)
    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    // 브리핑의 한 줄 결론 (값이 있으면 브리핑 구조의 글, 이전 구조만 있는 행은 null)
    @Column(name = "summary_one_line", length = 200)
    private String oneLine;

    // 브리핑: 무슨 일
    @Column(name = "summary_what_happened", length = 600)
    private String whatHappened;

    // 브리핑: 왜 중요 (입력에 영향이 적혀 있지 않으면 null)
    @Column(name = "summary_why_it_matters", length = 500)
    private String whyItMatters;

    // 브리핑: 영향 대상 (JSON 배열 [{target, detail}], ArticleBriefJson)
    @Column(name = "summary_affected", columnDefinition = "text")
    private String affectedJson;

    // 브리핑: 대응 (JSON 문자열 배열, ArticleBriefJson)
    @Column(name = "summary_actions", columnDefinition = "text")
    private String actionsJson;

    // 이전 구조의 요약 (브리핑이 있으면 한 줄 결론 + 무슨 일 + 왜 중요, DONE 일 때만 값 있음)
    @Column(name = "summary", length = 1500)
    private String summary;

    // 이전 구조의 핵심 정리 (JSON 문자열 배열, SummaryKeyPointJson, 브리핑이 있으면 영향 대상 줄)
    @Column(name = "summary_key_points", columnDefinition = "text")
    private String keyPointsJson;

    // 이전 구조의 대응 안내 (브리핑이 있으면 대응을 이은 글, 없으면 null)
    @Column(name = "summary_action_guide", length = 800)
    private String actionGuide;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_status", nullable = false, length = 16)
    private SummaryStatus status;

    @Column(name = "summary_attempt_count", nullable = false)
    private int attemptCount;

    /**
     * 새 기사: 요약 입력이 있으면 대기(PENDING), 없으면 NONE
     */
    static ArticleSummaryState ofSourceText(String sourceText) {
        ArticleSummaryState summaryState = new ArticleSummaryState();
        summaryState.restart(sourceText);
        return summaryState;
    }

    /**
     * 새 요약 입력으로 다시 대기 (null 이면 NONE): 이전 요약·시도 횟수 비움 (새 기사, NVD 링크 교체, 뒤늦게 받은 입력)
     */
    void restart(String newSourceText) {
        this.sourceText = newSourceText;
        applyBriefFields(null);
        applyLegacyFields(null);
        this.status = newSourceText == null ? SummaryStatus.NONE : SummaryStatus.PENDING;
        this.attemptCount = 0;
    }

    /**
     * 브리핑 반영 (검증 통과분): 브리핑 열 5개 + 이전 구조 열 3개 > DONE, 요약 입력 비움
     */
    public void applyBrief(ArticleBrief acceptedBrief) {
        applyBriefFields(acceptedBrief);
        applyLegacyFields(acceptedBrief.toLegacySummary());
        this.status = SummaryStatus.DONE;
        this.sourceText = null;
    }

    private void applyBriefFields(ArticleBrief brief) {
        this.oneLine = brief == null ? null : brief.oneLine();
        this.whatHappened = brief == null ? null : brief.whatHappened();
        this.whyItMatters = brief == null ? null : brief.whyItMatters();
        this.affectedJson = brief == null ? null : ArticleBriefJson.toAffectedJson(brief.affected());
        this.actionsJson = brief == null ? null : ArticleBriefJson.toActionsJson(brief.actions());
    }

    private void applyLegacyFields(ArticleSummary legacySummary) {
        this.summary = legacySummary == null ? null : legacySummary.summary();
        this.keyPointsJson = legacySummary == null ? null : SummaryKeyPointJson.toJson(legacySummary.keyPoints());
        this.actionGuide = legacySummary == null ? null : legacySummary.actionGuide();
    }

    /**
     * 요약 실패 기록: 시도 3회째면 FAILED + 요약 입력 비움, 아니면 PENDING 유지
     */
    public void recordFailure() {
        this.attemptCount++;
        if (attemptCount >= MAXIMUM_ATTEMPT_COUNT) {
            this.status = SummaryStatus.FAILED;
            this.sourceText = null;
        }
    }

    /** 화면에 보여 줄 요약이 있는지 (브리핑 또는 이전 구조) */
    public boolean isSummarized() {
        return status == SummaryStatus.DONE && summary != null;
    }

    /** 요약을 기다리는지 (입력 있음 + 실패 3회 전). 입력 없음·실패는 false: 화면이 "준비 중"을 끝없이 보이지 않게 */
    public boolean isPending() {
        return status == SummaryStatus.PENDING;
    }

    /** 브리핑 구조의 글이 있는지 (이전 구조만 있는 행, 한 줄 결론이 빈 행은 false: 앱은 한 줄 결론이 없는 브리핑을 쓰지 않음) */
    public boolean hasBrief() {
        return isSummarized() && oneLine != null && !oneLine.isBlank();
    }

    /** 브리핑 (hasBrief 일 때만 부름) */
    public ArticleBrief getBrief() {
        return new ArticleBrief(oneLine, whatHappened, whyItMatters,
                ArticleBriefJson.toAffectedTargets(affectedJson), ArticleBriefJson.toActions(actionsJson));
    }

    public List<String> getKeyPoints() {
        return SummaryKeyPointJson.toKeyPoints(keyPointsJson);
    }
}
