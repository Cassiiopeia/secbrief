package kr.suhsaechan.secbrief.advisory.service;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.SourceStatusResponse;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출처 상태 목록: 켜진 출처만, id 순
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceQueryService {

    private final SourceRepository sourceRepository;

    public List<SourceStatusResponse> findEnabledSources() {
        return sourceRepository.findAllByIsEnabledTrueOrderByIdAsc().stream().map(SourceStatusResponse::from).toList();
    }
}
