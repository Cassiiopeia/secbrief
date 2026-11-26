package kr.suhsaechan.secbrief.application.admin.source;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출처 관리: 전체 출처 표(꺼진 출처 포함, 최근 실패 사유), 켜기·끄기와 수집 주기 변경
 */
@Service
@RequiredArgsConstructor
public class AdminSourceService {

    private static final String SOURCE_ID_FIELD = "id";

    private final SourceRepository sourceRepository;
    private final AdminSourceFailureQueryRepository adminSourceFailureQueryRepository;

    @Transactional(readOnly = true)
    public List<AdminSourceRow> findAllSources() {
        Map<Long, AdminSourceFailure> latestFailureBySourceId = adminSourceFailureQueryRepository.findLatestFailures().stream()
                .collect(Collectors.toMap(AdminSourceFailure::sourceId, Function.identity()));
        return sourceRepository.findAll(Sort.by(SOURCE_ID_FIELD)).stream()
                .map(source -> toRow(source, latestFailureBySourceId.get(source.getId())))
                .toList();
    }

    /**
     * 수집 설정 변경: 주기 범위 검사(5~1440분) > 출처 조회 > 켜기·끄기와 주기 저장
     */
    @Transactional
    public SourceSettingChange updateCollectSettings(long sourceId, boolean isEnabled, int intervalMinutes) {
        if (intervalMinutes < Source.MINIMUM_INTERVAL_MINUTES || intervalMinutes > Source.MAXIMUM_INTERVAL_MINUTES) {
            throw new CustomException(ErrorCode.SOURCE_INTERVAL_OUT_OF_RANGE);
        }
        Source source = sourceRepository.findById(sourceId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        SourceSettingChange change = new SourceSettingChange(source.getCode(), source.getDisplayName(),
                source.isEnabled(), isEnabled, source.getIntervalMinutes(), intervalMinutes);
        source.changeCollectSettings(isEnabled, intervalMinutes);
        return change;
    }

    private static AdminSourceRow toRow(Source source, AdminSourceFailure latestFailure) {
        return new AdminSourceRow(source.getId(), source.getCode(), source.getDisplayName(),
                AdminDisplayLabels.ofArticleKind(source.getKind()), source.getIntervalMinutes(), source.isEnabled(),
                source.getLastCollectedAt(), source.getLastStatus(), AdminDisplayLabels.ofCollectStatus(source.getLastStatus()),
                latestFailure == null ? null : latestFailure.toReason(),
                latestFailure == null ? null : latestFailure.failedAt());
    }
}
