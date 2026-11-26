package kr.suhsaechan.secbrief.member.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import kr.suhsaechan.secbrief.member.entity.DigestItem;
import kr.suhsaechan.secbrief.member.entity.DigestItemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DigestItemRepository extends JpaRepository<DigestItem, DigestItemId> {

    // 대기 기사 1건 넣기 (같은 기기·기사가 이미 있으면 그대로)
    @Modifying
    @Query(nativeQuery = true, value = "insert into push_device_digest_item (push_device_id, article_id, queued_at) "
            + "values (:pushDeviceId, :articleId, :queuedAt) on conflict do nothing")
    int insertIgnoringDuplicate(@Param("pushDeviceId") long pushDeviceId, @Param("articleId") long articleId,
                                @Param("queuedAt") Instant queuedAt);

    // 대기분 전체 (기기·넣은 순서대로)
    @Query("select d from DigestItem d order by d.id.pushDeviceId asc, d.queuedAt asc, d.id.articleId asc")
    List<DigestItem> findAllInQueueOrder();

    // 요약을 보낸 기기의 대기분 지우기
    @Modifying
    @Query("delete from DigestItem d where d.id.pushDeviceId = :pushDeviceId and d.id.articleId in :articleIds")
    int deleteSent(@Param("pushDeviceId") long pushDeviceId, @Param("articleIds") Collection<Long> articleIds);

    // 오래된 대기분 버림 (48시간)
    @Modifying
    @Query("delete from DigestItem d where d.queuedAt < :queuedBefore")
    int deleteQueuedBefore(@Param("queuedBefore") Instant queuedBefore);
}
