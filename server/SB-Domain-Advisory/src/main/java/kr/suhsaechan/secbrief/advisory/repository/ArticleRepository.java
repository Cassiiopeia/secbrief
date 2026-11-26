package kr.suhsaechan.secbrief.advisory.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findByCveId(String cveId);

    Optional<Article> findByOriginalUrl(String originalUrl);

    // CVE 병합용 행 잠금 조회: 두 출처가 같은 행을 동시에 병합할 때 한쪽 변경이 덮이지 않게 함 (트랜잭션 안에서만)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.cveId = :cveId")
    Optional<Article> findByCveIdForUpdate(@Param("cveId") String cveId);

    // 저장돼 있고 더 채울 것이 없는 원문 링크만 (수집 묶음에서 건너뛸 것, 목록 나누기는 ArticleSaveService 가 함)
    // 요약 입력·이미지 주소가 둘 다 없는(NONE) 행은 빼서 다시 수집한 값으로 채울 기회를 줌 (기능 이전에 쌓인 기사)
    @Query("select a.originalUrl from Article a where a.originalUrl in :originalUrls "
            + "and (a.summaryState.status <> kr.suhsaechan.secbrief.advisory.constant.SummaryStatus.NONE "
            + "or a.imageState.status <> kr.suhsaechan.secbrief.advisory.constant.ImageStatus.NONE)")
    List<String> findSettledOriginalUrls(@Param("originalUrls") Collection<String> originalUrls);

    // AI 처리 대기열: 게시일 최신순 (같으면 id 큰 것) limit 건 (새 소식이 오래된 대기 건에 밀리지 않게)
    List<Article> findByAiStatusOrderByPublishedAtDescIdDesc(AiStatus aiStatus, Limit limit);

    // 이미지 저장 대기열: 최신 항목부터 limit 건 (화면 위쪽에 보이는 기사 먼저)
    List<Article> findByImageState_StatusOrderByIdDesc(ImageStatus imageStatus, Limit limit);

    // 저장된 대표 이미지의 객체 키 (STORED 가 아니면 비어 있음)
    @Query("select a.imageState.objectKey from Article a where a.id = :articleId "
            + "and a.imageState.status = kr.suhsaechan.secbrief.advisory.constant.ImageStatus.STORED")
    Optional<String> findStoredImageObjectKey(@Param("articleId") Long articleId);

    // 긴급 알림 대기열: 긴급 + 미발송, AI 처리가 끝났거나 AI 대기 기한(aiWaitDeadline 이전 수집)을 넘긴 것 (번역 제목으로 알리려고 기다림)
    @Query("select a from Article a join fetch a.source "
            + "where a.isUrgent = true and a.urgentNotifiedAt is null "
            + "and (a.aiStatus <> kr.suhsaechan.secbrief.advisory.constant.AiStatus.PENDING or a.collectedAt <= :aiWaitDeadline) "
            + "order by a.id asc")
    List<Article> findUrgentAlertTargets(@Param("aiWaitDeadline") Instant aiWaitDeadline, Limit limit);

    // 긴급 알림 발송 처리 표시 (받을 기기가 없어도 처리한 것으로 표시)
    @Modifying
    @Query("update Article a set a.urgentNotifiedAt = :notifiedAt where a.id in :articleIds")
    int markUrgentNotified(@Param("articleIds") Collection<Long> articleIds, @Param("notifiedAt") Instant notifiedAt);

    // 게시일 start 이후 건수 (오늘·이번 주 건수)
    long countByPublishedAtGreaterThanEqual(Instant start);

    // 주제별 건수: 게시일 [start, end)
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.TopicCount(a.topic, count(a)) "
            + "from Article a where a.publishedAt >= :start and a.publishedAt < :end group by a.topic")
    List<TopicCount> countByTopicBetween(@Param("start") Instant start, @Param("end") Instant end);

    // 키워드별 건수: 게시일 [start, end), 저장된 표기 그대로 (대소문자 합치기는 KeywordTrendQueryService 가 함)
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.KeywordCount(k, count(a)) "
            + "from Article a join a.keywords k where a.publishedAt >= :start and a.publishedAt < :end group by k")
    List<KeywordCount> countByKeywordBetween(@Param("start") Instant start, @Param("end") Instant end);

    // 요약 기록용 행 잠금 조회: 수집 병합(NVD 링크 교체)과 요약 기록이 같은 행의 요약 열을 서로 덮지 않게 함 (트랜잭션 안에서만)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Article a where a.id = :articleId")
    Optional<Article> findByIdForUpdate(@Param("articleId") Long articleId);

    // 요약 대기열: 중요도 높은 순 (HEADLINE > NOTABLE > ROUTINE) > 최신순, limit 건
    @Query("select a from Article a "
            + "where a.summaryState.status = kr.suhsaechan.secbrief.advisory.constant.SummaryStatus.PENDING "
            + "order by case when a.importance = kr.suhsaechan.secbrief.advisory.constant.ArticleImportance.HEADLINE then 0 "
            + "when a.importance = kr.suhsaechan.secbrief.advisory.constant.ArticleImportance.NOTABLE then 1 else 2 end, "
            + "a.publishedAt desc, a.id desc")
    List<Article> findSummaryTargets(Limit limit);

    // 관련 기사 ID (자기 제외, since 이후 게시분): 같은 CVE(0) > 같은 회사·제품(1) > 겹치는 키워드(2, 겹친 수 많은 순) > 같은 주제(3),
    // 같은 순위 안에서는 최신순. 주제가 GENERAL(분류 전·그 밖)이면 같은 주제로 묶지 않음 (무관한 최신 기사가 채워짐). cveIdPattern·subjectName 이 빈 문자열이면 그 조건은 건너뜀 (RelatedArticlePolicy 가 만듦)
    @Query(nativeQuery = true, value = """
            select ranked.id
              from (select a.id, a.published_at,
                           coalesce(shared.shared_keyword_count, 0) as shared_keyword_count,
                           case when :cveIdPattern <> '' and (a.cve_id ~* :cveIdPattern or a.title ~* :cveIdPattern) then 0
                                when :subjectName <> '' and lower(a.subject_name) = lower(:subjectName) then 1
                                when shared.shared_keyword_count is not null then 2
                                when :topic <> 'GENERAL' and a.topic = :topic then 3
                            end as match_rank
                      from article a
                      left join (select other.article_id, count(*) as shared_keyword_count
                                   from article_keyword mine
                                   join article_keyword other on other.keyword = mine.keyword
                                  where mine.article_id = :articleId and other.article_id <> :articleId
                                  group by other.article_id) shared on shared.article_id = a.id
                     where a.id <> :articleId and a.published_at >= :since) ranked
             where ranked.match_rank is not null
             order by ranked.match_rank, ranked.shared_keyword_count desc, ranked.published_at desc, ranked.id desc
             limit :maximumCount
            """)
    List<Long> findRelatedArticleIds(@Param("articleId") Long articleId, @Param("cveIdPattern") String cveIdPattern,
                                     @Param("subjectName") String subjectName, @Param("topic") String topic,
                                     @Param("since") Instant since, @Param("maximumCount") int maximumCount);
}
