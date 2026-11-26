package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.time.Clock;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 호출 기록 조회 (관리자 화면·일일 예산): 최근 호출 목록(용도·성공 여부 필터), 오늘·이번 달 합계 (서울 날짜)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiCallLogQueryService {

    // 호출 목록 한 쪽 건수
    public static final int PAGE_SIZE = 50;

    private final AiCallLogRepository aiCallLogRepository;
    private final Clock clock;

    /**
     * 최근 호출 목록: 최신순, callType·isSuccess 가 null 이면 그 조건 없이
     */
    public Page<AiCallLog> findRecentCalls(AiCallType callType, Boolean isSuccess, int pageNumber) {
        Specification<AiCallLog> condition = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (callType != null) {
            condition = condition.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("callType"), callType));
        }
        if (isSuccess != null) {
            condition = condition.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("isSuccess"), isSuccess));
        }
        return aiCallLogRepository.findAll(condition,
                PageRequest.of(Math.max(0, pageNumber), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
    }

    public AiCallSummary summarizeToday() {
        return aiCallLogRepository.summarizeSince(SeoulCalendar.startOfToday(clock.instant()));
    }

    public AiCallSummary summarizeThisMonth() {
        Instant startOfMonth = SeoulCalendar.toSeoulDate(clock.instant())
                .withDayOfMonth(1)
                .atStartOfDay(SeoulCalendar.SEOUL)
                .toInstant();
        return aiCallLogRepository.summarizeSince(startOfMonth);
    }
}
