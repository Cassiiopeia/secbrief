package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.summary.SummaryKeyPointJson;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기사의 AI 요약 상태 (article 표의 요약 열 6개): 요약 입력, 요약, 핵심 정리, 대응 안내, 상태, 시도 횟수
 * 입력 있음(PENDING) > 요약 반영(DONE) 또는 3회 실패(FAILED), 끝나면 요약 입력을 비움 (본문을 보관하지 않음)
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

    // AI 가 쓴 한국어 요약 (검증 통과분, DONE 일 때만 값 있음)
    @Column(name = "summary", length = 1500)
    private String summary;

    // 핵심 정리 (JSON 문자열 배열, SummaryKeyPointJson)
    @Column(name = "summary_key_points", columnDefinition = "text")
    private String keyPointsJson;

    // 대응 안내: 입력에 적힌 조치만 (없으면 null)
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
        this.summary = null;
        this.keyPointsJson = null;
        this.actionGuide = null;
        this.status = newSourceText == null ? SummaryStatus.NONE : SummaryStatus.PENDING;
        this.attemptCount = 0;
    }

    /**
     * 요약 반영 (검증 통과분) > DONE, 요약 입력 비움
     */
    public void applySummary(String acceptedSummary, List<String> acceptedKeyPoints, String acceptedActionGuide) {
        this.summary = acceptedSummary;
        this.keyPointsJson = SummaryKeyPointJson.toJson(acceptedKeyPoints);
        this.actionGuide = acceptedActionGuide;
        this.status = SummaryStatus.DONE;
        this.sourceText = null;
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

    /** 화면에 보여 줄 요약이 있는지 */
    public boolean isSummarized() {
        return status == SummaryStatus.DONE && summary != null;
    }

    public List<String> getKeyPoints() {
        return SummaryKeyPointJson.toKeyPoints(keyPointsJson);
    }
}
