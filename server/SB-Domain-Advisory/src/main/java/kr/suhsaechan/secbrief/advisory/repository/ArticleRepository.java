package kr.suhsaechan.secbrief.advisory.repository;

import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByCveId(String cveId);

    boolean existsByOriginalUrl(String originalUrl);

    // AI 처리 대기열: 오래된 항목부터 limit 건
    List<Article> findByAiStatusOrderByIdAsc(AiStatus aiStatus, Limit limit);
}
