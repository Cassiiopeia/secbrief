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

    // 알림 판정 대기열: 처리 전 + AI 판단이 끝나 분류된 것, 또는 AI 대기 기한(holdDeadline 이전 수집)을 넘긴 것 (#97: 판단 전에 보내지 않음)
    @Query("select a from Article a join fetch a.source "
            + "where a.urgentNotifiedAt is null "
            + "and ((a.aiStatus <> kr.suhsaechan.secbrief.advisory.constant.AiStatus.PENDING and a.classifiedAt is not null) "
            + "or a.collectedAt <= :holdDeadline) "
            + "order by a.id asc")
    List<Article> findAlertCandidates(@Param("holdDeadline") Instant holdDeadline, Limit limit);

    // 분류 대기열: 분류 전 + (AI 판단이 끝남 또는 AI 대기 기한을 넘김), 오래된 것부터 (백필·다시 분류 포함)
    @Query("select a from Article a join fetch a.source "
            + "where a.classifiedAt is null "
            + "and (a.aiStatus <> kr.suhsaechan.secbrief.advisory.constant.AiStatus.PENDING or a.collectedAt <= :holdDeadline) "
            + "order by a.id asc")
    List<Article> findClassificationTargets(@Param("holdDeadline") Instant holdDeadline, Limit limit);

    // 전체 다시 분류 (관리자 화면, 알림 처리 시각은 건드리지 않아 다시 알리지 않음)
    @Modifying
    @Query("update Article a set a.classifiedAt = null")
    int requestReclassificationOfAll();

    // 긴급 알림 발송 처리 표시 (받을 기기가 없어도 처리한 것으로 표시)
    @Modifying
    @Query("update Article a set a.urgentNotifiedAt = :notifiedAt where a.id in :articleIds")
    int markUrgentNotified(@Param("articleIds") Collection<Long> articleIds, @Param("notifiedAt") Instant notifiedAt);

    // 수집 시각 since 이후 분류 값 (오늘 새 소식 건수, 최신 수집 먼저)
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.NewArticleTagRow(a.source.code, a.kind, a.isKev, "
            + "a.fieldCode, a.ecosystemCode, a.productCode, a.packageName, a.subjectName, a.collectedAt) "
            + "from Article a where a.collectedAt >= :since order by a.collectedAt desc, a.id desc")
    List<NewArticleTagRow> findNewArticleTagRows(@Param("since") Instant since);

    // 수집 시각 [start, end) 기사 (어제 기준 알림 추정)
    @Query("select a from Article a join fetch a.source where a.collectedAt >= :start and a.collectedAt < :end order by a.id asc")
    List<Article> findAllCollectedBetween(@Param("start") Instant start, @Param("end") Instant end);

    // 게시일 start 이후 건수 (오늘·이번 주 건수)
    long countByPublishedAtGreaterThanEqual(Instant start);

    // 긴급 기간이 지난 출처 글의 긴급 표시 해제: sourceCode 출처의 긴급 글 중 게시일이 expiredBefore 이전 (KISA 경보단계, #96)
    @Modifying
    @Query("update Article a set a.isUrgent = false where a.isUrgent = true and a.publishedAt < :expiredBefore "
            + "and a.source.id in (select s.id from Source s where s.code = :sourceCode)")
    int clearUrgentPublishedBefore(@Param("sourceCode") String sourceCode, @Param("expiredBefore") Instant expiredBefore);

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

    // 요약 입력 다시 받기 대상 (서버 다시 요약, 기본 꺼짐): 브리핑 없음 + 요약 입력 없음 + 이전 구조 요약(DONE)·입력 없음(NONE),
    // 다시 받는 출처만 (SourceTextRefetchMethod), beforeId 미만 id 내림차순 (FAILED 는 다시 부르지 않음)
    @Query("select a from Article a "
            + "where a.id < :beforeId and a.summaryState.oneLine is null and a.summaryState.sourceText is null "
            + "and a.summaryState.status in (kr.suhsaechan.secbrief.advisory.constant.SummaryStatus.DONE, "
            + "kr.suhsaechan.secbrief.advisory.constant.SummaryStatus.NONE) "
            + "and a.source.code in :sourceCodes order by a.id desc")
    List<Article> findSourceTextRefetchTargets(@Param("beforeId") long beforeId,
                                               @Param("sourceCodes") Collection<String> sourceCodes, Limit limit);

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

    // 대응 모아보기 후보 (자기 제외, ResponseGuidePolicy 가 기간·이름을 만듦): 같은 CVE(0) > 같은 제품 이름(1) > 같은 제조사 공지(2),
    // 같은 순위 안에서는 KEV 등재 > CVSS 높은 순 > 최신순. 이름 비교는 소문자 + 공백·문장부호를 공백 하나로 바꾼 값
    // 0: cveIdPattern 이 cve_id·제목에 맞고 cveSince 이후 게시 (cveIdPattern 이 빈 문자열이면 건너뜀)
    // 1: 이름이 같고 어느 쪽도 제조사 공지가 아님, 2: 한쪽이 제조사 공지(vendorNoticeSourceCode)이고 그 이름 = 상대 이름 또는 상대 이름의 앞 낱말
    // 1·2 공통: productName 이 빈 문자열이 아니고, 후보 게시일이 [productFrom, productTo], 후보 주제가 excludedTopics 밖
    @Query(nativeQuery = true, value = """
            select ranked.id as "articleId", ranked.match_rank as "matchRank"
              from (select a.id, a.is_kev, a.cvss_score, a.published_at,
                           case when :cveIdPattern <> '' and a.published_at >= :cveSince
                                     and (a.cve_id ~* :cveIdPattern or a.title ~* :cveIdPattern) then 0
                                when viewed.name = '' or names.name is null or names.name = ''
                                     or a.topic in (:excludedTopics)
                                     or a.published_at < :productFrom or a.published_at > :productTo then null
                                when s.code <> :vendorNoticeSourceCode and not :isViewedVendorNotice
                                     and names.name = viewed.name then 1
                                when s.code = :vendorNoticeSourceCode
                                     and (viewed.name = names.name or viewed.name like names.name || ' %') then 2
                                when :isViewedVendorNotice
                                     and (names.name = viewed.name or names.name like viewed.name || ' %') then 2
                            end as match_rank
                      from article a
                      join source s on s.id = a.source_id
                      cross join (select btrim(regexp_replace(lower(:productName), '[[:space:][:punct:]]+', ' ', 'g')) as name) viewed
                      cross join lateral (select btrim(regexp_replace(lower(a.subject_name), '[[:space:][:punct:]]+', ' ', 'g')) as name) names
                     where a.id <> :articleId) ranked
             where ranked.match_rank is not null
             order by ranked.match_rank, ranked.is_kev desc, ranked.cvss_score desc nulls last, ranked.published_at desc, ranked.id desc
             limit :maximumCount
            """)
    List<ResponseGuideCandidate> findResponseGuideCandidates(@Param("articleId") Long articleId,
                                                             @Param("cveIdPattern") String cveIdPattern,
                                                             @Param("cveSince") Instant cveSince,
                                                             @Param("productName") String productName,
                                                             @Param("productFrom") Instant productFrom,
                                                             @Param("productTo") Instant productTo,
                                                             @Param("excludedTopics") Collection<String> excludedTopics,
                                                             @Param("vendorNoticeSourceCode") String vendorNoticeSourceCode,
                                                             @Param("isViewedVendorNotice") boolean isViewedVendorNotice,
                                                             @Param("maximumCount") int maximumCount);
}
