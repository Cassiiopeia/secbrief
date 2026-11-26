package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.TodayResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendTopicResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 화면: 긴급 1건(72시간 안 최신) > 최신 목록 20건(긴급 제외, 뉴스·국내 공지·주요 소식만) > 오늘·이번 주 건수 > 동향 1위(general 제외)
 * > 주요 소식 6건(72시간 안, 중요도 높은 순)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayQueryService {

    // 긴급 항목을 오늘 화면 맨 위에 두는 기간
    public static final Duration URGENT_VISIBLE_DURATION = Duration.ofHours(72);
    public static final int TODAY_STREAM_SIZE = 20;
    // 주요 소식: 이 기간 안의 중요도 NOTABLE 이상, 최대 건수
    public static final Duration HEADLINE_VISIBLE_DURATION = Duration.ofHours(72);
    public static final int TODAY_HEADLINE_SIZE = 6;

    private final ArticleRepository articleRepository;
    private final WeeklyTrendQueryService weeklyTrendQueryService;
    private final Clock clock;

    public TodayResponse findToday() {
        Instant now = clock.instant();
        Optional<Article> urgentArticle = findLatest(ArticleSpecifications.isUrgentPublishedSince(now.minus(URGENT_VISIBLE_DURATION)), 1)
                .stream().findFirst();
        Specification<Article> streamSpecification = urgentArticle
                .map(article -> ArticleSpecifications.isBriefingStreamItem()
                        .and(ArticleSpecifications.hasIdOtherThan(article.getId())))
                .orElse(ArticleSpecifications.isBriefingStreamItem());

        return new TodayResponse(
                SeoulCalendar.toSeoulDate(now),
                urgentArticle.map(ArticleResponse::from).orElse(null),
                articleRepository.countByPublishedAtGreaterThanEqual(SeoulCalendar.startOfToday(now)),
                articleRepository.countByPublishedAtGreaterThanEqual(SeoulCalendar.startOfWeek(now)),
                findTopTrend().orElse(null),
                findLatest(streamSpecification, TODAY_STREAM_SIZE).stream().map(ArticleResponse::from).toList(),
                findHeadlines(now).stream().map(ArticleResponse::from).toList());
    }

    private List<Article> findHeadlines(Instant now) {
        Specification<Article> headlineSpecification = Specification.allOf(
                ArticleSpecifications.hasImportanceAtLeast(ArticleImportance.NOTABLE),
                ArticleSpecifications.isPublishedSince(now.minus(HEADLINE_VISIBLE_DURATION)));
        return articleRepository.findBy(headlineSpecification,
                query -> query.sortBy(ArticleSpecifications.MOST_IMPORTANT_FIRST).limit(TODAY_HEADLINE_SIZE).all());
    }

    private List<Article> findLatest(Specification<Article> specification, int limit) {
        return articleRepository.findBy(specification,
                query -> query.sortBy(ArticleSpecifications.NEWEST_FIRST).limit(limit).all());
    }

    // 주간 동향 중 general 제외, 이번 주 1건 이상인 첫 주제
    private Optional<TrendTopicResponse> findTopTrend() {
        return weeklyTrendQueryService.findWeeklyTrend().topics().stream()
                .filter(trend -> trend.topic() != ArticleTopic.GENERAL && trend.weeklyCount() > 0)
                .findFirst();
    }
}
