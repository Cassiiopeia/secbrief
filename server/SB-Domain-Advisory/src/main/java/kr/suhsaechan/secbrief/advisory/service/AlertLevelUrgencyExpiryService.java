package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KISA 경보단계 글 긴급 기간 정리: 게시 후 7일이 지난 글의 긴급 표시 해제 (목록 배지·긴급 모아 보기·건수가 같은 값을 봄)
 * 새로 수집하는 지난 글은 수집 단계(ArticleUrgencyPolicy.isUrgentAt)가 처음부터 긴급으로 두지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertLevelUrgencyExpiryService {

    private final ArticleRepository articleRepository;
    private final Clock clock;

    @Transactional
    public int expireAlertLevelUrgency() {
        int clearedCount = articleRepository.clearUrgentPublishedBefore(SourceCode.KISA_ALERT_LEVEL,
                clock.instant().minus(ArticleUrgencyPolicy.KISA_ALERT_LEVEL_URGENT_DURATION));
        if (clearedCount > 0) {
            log.info("[alert level urgency] cleared : count={}", clearedCount);
        }
        return clearedCount;
    }
}
