package kr.suhsaechan.secbrief.web.admin.controller;

import java.util.Arrays;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLog;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogQueryService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.web.admin.support.AdminAiCallRow;
import kr.suhsaechan.secbrief.web.admin.support.AdminPagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI 호출 화면: 오늘·이번 달 합계 카드, 최근 호출 표(용도·성공 여부 필터) + 쪽 번호 (읽기 전용)
 */
@Controller
@RequiredArgsConstructor
public class AdminAiCallController {

    private static final String AI_CALLS_PATH = "/admin/ai-calls";
    private static final String AI_CALLS_VIEW = "admin/ai-calls";
    // result 필터 값: success 성공만, failure 실패만, 그 외 전체
    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";

    private final AiCallLogQueryService aiCallLogQueryService;

    @GetMapping(AI_CALLS_PATH)
    public String showAiCalls(@RequestParam(name = "callType", required = false) String callTypeParameter,
                              @RequestParam(name = "result", required = false) String resultParameter,
                              @RequestParam(name = "page", defaultValue = "1") String pageParameter, Model model) {
        AiCallType callType = toCallType(callTypeParameter);
        Boolean isSuccess = toSuccessFilter(resultParameter);
        Page<AiCallLog> callLogPage = aiCallLogQueryService.findRecentCalls(callType, isSuccess,
                parsePageNumber(pageParameter) - 1);

        model.addAttribute("callRows", callLogPage.getContent().stream().map(AdminAiCallRow::of).toList());
        model.addAttribute("pagination", AdminPagination.of(callLogPage));
        model.addAttribute("paginationBasePath", toPaginationBasePath(callType, isSuccess));
        model.addAttribute("callTypes", AiCallType.values());
        model.addAttribute("selectedCallType", callType);
        model.addAttribute("selectedResult", isSuccess == null ? "" : (isSuccess ? RESULT_SUCCESS : RESULT_FAILURE));
        model.addAttribute("todaySummary", aiCallLogQueryService.summarizeToday());
        model.addAttribute("monthSummary", aiCallLogQueryService.summarizeThisMonth());
        return AI_CALLS_VIEW;
    }

    // 모르는 값은 전체 (화면은 열림)
    private static AiCallType toCallType(String callTypeParameter) {
        return Arrays.stream(AiCallType.values())
                .filter(callType -> callType.name().equals(callTypeParameter))
                .findFirst()
                .orElse(null);
    }

    private static Boolean toSuccessFilter(String resultParameter) {
        if (RESULT_SUCCESS.equals(resultParameter)) {
            return Boolean.TRUE;
        }
        return RESULT_FAILURE.equals(resultParameter) ? Boolean.FALSE : null;
    }

    // 쪽 번호 링크가 지금 필터를 그대로 들고 가게 함
    private static String toPaginationBasePath(AiCallType callType, Boolean isSuccess) {
        UriComponentsBuilder basePath = UriComponentsBuilder.fromPath(AI_CALLS_PATH);
        if (callType != null) {
            basePath.queryParam("callType", callType.name());
        }
        if (isSuccess != null) {
            basePath.queryParam("result", isSuccess ? RESULT_SUCCESS : RESULT_FAILURE);
        }
        return basePath.build().toUriString();
    }

    // 숫자가 아니거나 0 이하면 1쪽
    private static int parsePageNumber(String pageParameter) {
        try {
            return Math.max(1, Integer.parseInt(pageParameter.trim()));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
