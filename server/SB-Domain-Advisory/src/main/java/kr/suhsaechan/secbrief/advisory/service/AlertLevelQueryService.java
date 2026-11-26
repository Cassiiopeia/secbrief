package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoard;
import kr.suhsaechan.secbrief.advisory.constant.AlertLevelChangeDirection;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelChangeResponse;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelDashboardResponse;
import kr.suhsaechan.secbrief.advisory.dto.AlertLevelStatusResponse;
import kr.suhsaechan.secbrief.advisory.dto.InternationalIndicatorResponse;
import kr.suhsaechan.secbrief.advisory.dto.WeeklyThreatCountsResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicator;
import kr.suhsaechan.secbrief.advisory.indicator.InternationalAlertIndicatorStore;
import kr.suhsaechan.secbrief.advisory.policy.CyberCrisisLevelChange;
import kr.suhsaechan.secbrief.advisory.policy.CyberCrisisLevelTitleParser;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경보단계 화면: 경보단계 글(최신순) > 변경 이력·지금 단계 > 이번 주 위협 건수 3개 > 해외 지표(보관값)
 * 단계는 제목에서만 읽음 (CyberCrisisLevelTitleParser), 못 읽은 글은 단계 null 로 두고 제목·원문 링크를 그대로 보여 줌
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertLevelQueryService {

    // 변경 이력 최대 건수 (게시판 전체 69건 중 최근분)
    public static final int MAXIMUM_CHANGE_COUNT = 20;

    private final ArticleRepository articleRepository;
    private final InternationalAlertIndicatorStore internationalAlertIndicatorStore;
    private final Clock clock;

    public AlertLevelDashboardResponse findDashboard() {
        // 한 건 더 읽음: 화면 마지막 변경의 바뀌기 전 단계를 그 앞 글에서 채움
        List<Article> alertLevelArticles = articleRepository.findBy(
                ArticleSpecifications.hasSourceCode(SourceCode.KISA_ALERT_LEVEL),
                query -> query.sortBy(ArticleSpecifications.NEWEST_FIRST).limit(MAXIMUM_CHANGE_COUNT + 1).all());
        List<AlertLevelChangeResponse> changes = toChanges(alertLevelArticles);
        return new AlertLevelDashboardResponse(
                toCurrentStatus(changes),
                changes.stream().limit(MAXIMUM_CHANGE_COUNT).toList(),
                countWeeklyThreats(clock.instant()),
                findInternationalIndicators(),
                KisaBoard.ALERT_LEVEL.toBoardUrl());
    }

    // 최신순 글 > 변경 (바뀌기 전 단계: 제목 > 바로 앞(더 오래된) 글의 바뀐 단계)
    private static List<AlertLevelChangeResponse> toChanges(List<Article> newestFirstArticles) {
        List<CyberCrisisLevelChange> parsedChanges = newestFirstArticles.stream()
                .map(article -> CyberCrisisLevelTitleParser.parse(article.getTitle()).orElse(null))
                .toList();
        List<AlertLevelChangeResponse> changes = new ArrayList<>();
        for (int index = 0; index < newestFirstArticles.size(); index++) {
            Article article = newestFirstArticles.get(index);
            CyberCrisisLevel toLevel = toLevelOrNull(parsedChanges.get(index));
            CyberCrisisLevel fromLevel = parsedChanges.get(index) != null && parsedChanges.get(index).fromLevel() != null
                    ? parsedChanges.get(index).fromLevel()
                    : (index + 1 < parsedChanges.size() ? toLevelOrNull(parsedChanges.get(index + 1)) : null);
            changes.add(new AlertLevelChangeResponse(article.getId(), fromLevel, toLevel,
                    AlertLevelChangeDirection.of(fromLevel, toLevel), article.getPublishedAt(), article.getTitle(),
                    article.getOriginalUrl()));
        }
        return changes;
    }

    private static CyberCrisisLevel toLevelOrNull(CyberCrisisLevelChange change) {
        return change == null ? null : change.toLevel();
    }

    // 가장 새 글의 단계 + 같은 단계가 이어진 첫 글의 게시 시각 (단계를 모르면 그 글의 시각)
    private static AlertLevelStatusResponse toCurrentStatus(List<AlertLevelChangeResponse> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        AlertLevelChangeResponse latest = changes.getFirst();
        Instant since = latest.publishedAt();
        for (AlertLevelChangeResponse change : changes) {
            if (latest.toLevel() == null || !Objects.equals(change.toLevel(), latest.toLevel())) {
                break;
            }
            since = change.publishedAt();
        }
        return new AlertLevelStatusResponse(latest.toLevel(), since, latest.articleId(), latest.title(),
                latest.originalUrl());
    }

    private WeeklyThreatCountsResponse countWeeklyThreats(Instant now) {
        Instant weekStart = SeoulCalendar.startOfWeek(now);
        Specification<Article> publishedThisWeek = ArticleSpecifications.isPublishedSince(weekStart);
        return new WeeklyThreatCountsResponse(
                weekStart,
                articleRepository.count(publishedThisWeek.and(ArticleSpecifications.hasSourceCode(SourceCode.CISA_KEV))),
                articleRepository.count(ArticleSpecifications.isUrgentPublishedSince(weekStart)),
                articleRepository.count(publishedThisWeek.and(ArticleSpecifications.hasSourceCode(SourceCode.KISA))));
    }

    private List<InternationalIndicatorResponse> findInternationalIndicators() {
        return Arrays.stream(InternationalAlertIndicator.values())
                .map(indicator -> internationalAlertIndicatorStore.findLatestReading(indicator)
                        .map(InternationalIndicatorResponse::from)
                        .orElseGet(() -> InternationalIndicatorResponse.unavailable(indicator)))
                .toList();
    }
}
