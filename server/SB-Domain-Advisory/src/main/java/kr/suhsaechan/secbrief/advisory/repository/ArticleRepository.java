package kr.suhsaechan.secbrief.advisory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByCveId(String cveId);

    boolean existsByOriginalUrl(String originalUrl);

    // AI 처리 대기열: 오래된 항목부터 limit 건
    List<Article> findByAiStatusOrderByIdAsc(AiStatus aiStatus, Limit limit);

    // 게시일 start 이후 건수 (오늘·이번 주 건수)
    long countByPublishedAtGreaterThanEqual(Instant start);

    // 주제별 건수: 게시일 [start, end)
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.TopicCount(a.topic, count(a)) "
            + "from Article a where a.publishedAt >= :start and a.publishedAt < :end group by a.topic")
    List<TopicCount> countByTopicBetween(@Param("start") Instant start, @Param("end") Instant end);
}
