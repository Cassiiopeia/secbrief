package kr.suhsaechan.secbrief.application.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisOutcome;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisRequest;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 테스트용 분석기: 제목별 응답을 미리 정해 둠 (정하지 않은 제목은 건별 실패), 실제 API 호출 없음
 * 호출 실패 제목이 묶음에 있으면 묶음 전체가 AI_API_ERROR, 받은 묶음 크기를 기록
 */
class FakeArticleTitleAnalyzer implements ArticleTitleAnalyzer {

    private final Map<String, ArticleTitleAnalysis> analysisByTitle = new HashMap<>();
    private final List<Integer> receivedBatchSizes = new ArrayList<>();
    private String callFailureTitle;
    private boolean isAvailable = true;

    void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    void respondTo(String title, ArticleTitleAnalysis analysis) {
        analysisByTitle.put(title, analysis);
    }

    void failCallContaining(String title) {
        this.callFailureTitle = title;
    }

    List<Integer> getReceivedBatchSizes() {
        return receivedBatchSizes;
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
    public List<ArticleTitleAnalysisOutcome> analyzeBatch(List<ArticleTitleAnalysisRequest> requests) {
        receivedBatchSizes.add(requests.size());
        if (requests.stream().anyMatch(request -> request.title().equals(callFailureTitle))) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "fake call failure");
        }
        return requests.stream()
                .map(request -> analysisByTitle.get(request.title()))
                .map(analysis -> analysis == null
                        ? ArticleTitleAnalysisOutcome.failed("no fake response")
                        : ArticleTitleAnalysisOutcome.succeeded(analysis))
                .toList();
    }
}
