package kr.suhsaechan.secbrief.application.summary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryOutcome;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 테스트용 요약기: 제목별 결과를 미리 정해 둠 (정하지 않은 제목은 실패 결과), 실제 API 호출 없음
 * 호출 실패 제목이면 AI_API_ERROR, 받은 요청을 기록
 */
class FakeArticleSummarizer implements ArticleSummarizer {

    private final Map<String, ArticleSummaryOutcome> outcomeByTitle = new HashMap<>();
    private final List<ArticleSummaryRequest> receivedRequests = new ArrayList<>();
    private String callFailureTitle;
    private boolean isAvailable = true;

    void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    void respondTo(String title, ArticleSummaryOutcome outcome) {
        outcomeByTitle.put(title, outcome);
    }

    void failCallFor(String title) {
        this.callFailureTitle = title;
    }

    List<ArticleSummaryRequest> getReceivedRequests() {
        return receivedRequests;
    }

    @Override
    public String getProviderCode() {
        return "fake";
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public ArticleSummaryOutcome summarize(ArticleSummaryRequest request) {
        receivedRequests.add(request);
        if (request.title().equals(callFailureTitle)) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "fake call failure");
        }
        return outcomeByTitle.getOrDefault(request.title(), ArticleSummaryOutcome.failed("no fake response"));
    }
}
