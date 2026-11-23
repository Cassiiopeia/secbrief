package kr.suhsaechan.secbrief.application.ai;

import java.util.HashMap;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisRequest;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 테스트용 분석기: 제목별 응답을 미리 정해 둠 (정하지 않은 제목은 AI_API_ERROR), 실제 API 호출 없음
 */
class FakeArticleTitleAnalyzer implements ArticleTitleAnalyzer {

    private final Map<String, ArticleTitleAnalysis> analysisByTitle = new HashMap<>();
    private boolean isAvailable = true;

    void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    void respondTo(String title, ArticleTitleAnalysis analysis) {
        analysisByTitle.put(title, analysis);
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public ArticleTitleAnalysis analyze(ArticleTitleAnalysisRequest request) {
        ArticleTitleAnalysis analysis = analysisByTitle.get(request.title());
        if (analysis == null) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "no fake response");
        }
        return analysis;
    }
}
