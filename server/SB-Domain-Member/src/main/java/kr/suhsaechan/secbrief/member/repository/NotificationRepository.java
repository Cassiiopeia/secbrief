package kr.suhsaechan.secbrief.member.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 목록 정렬: 고정 먼저 > 최신순 > 번호 (ALERT 는 고정이 없어 최신순)
    String LIST_ORDER = " order by n.isPinned desc, n.createdAt desc, n.id desc";

    // 목록 첫 페이지
    @Query("select n from Notification n where n.kind = :kind and n.pushDeviceId is null" + LIST_ORDER)
    List<Notification> findFirstPage(@Param("kind") NotificationKind kind, Limit limit);

    // 목록 다음 페이지: 커서(고정 여부, 만든 시각, 번호) 뒤 (고정 구간이 끝나면 고정 아닌 구간 처음부터)
    @Query("select n from Notification n where n.kind = :kind and n.pushDeviceId is null and ("
            + "(:isCursorPinned = true and (n.isPinned = false or (n.isPinned = true and "
            + "(n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id)))))"
            + " or (:isCursorPinned = false and n.isPinned = false and "
            + "(n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))))"
            + LIST_ORDER)
    List<Notification> findPageAfter(@Param("kind") NotificationKind kind, @Param("isCursorPinned") boolean isCursorPinned,
                                     @Param("createdAt") Instant createdAt, @Param("id") long id, Limit limit);

    // 관리자 공지 표: 전체 (고정 먼저 > 최신순)
    List<Notification> findAllByKindOrderByIsPinnedDescCreatedAtDescIdDesc(NotificationKind kind);

    // 종류별 가장 최근 공통 기록 번호 (앱 알림 점: 본 번호보다 크면 새 알림, 기기 전용 기록은 뺌)
    Optional<Notification> findTopByKindAndPushDeviceIdIsNullOrderByIdDesc(NotificationKind kind);

    // 기간 안 공통 기록 (묶인 기사까지, 알림 추정)
    @Query("select distinct n from Notification n left join fetch n.articles "
            + "where n.kind = :kind and n.pushDeviceId is null and n.createdAt >= :start and n.createdAt < :end")
    List<Notification> findSharedCreatedBetween(@Param("kind") NotificationKind kind, @Param("start") Instant start,
                                                @Param("end") Instant end);

    // 기간 안 공통 알림 기록 수 (관심 기술 화면의 "지금은 N번")
    long countByKindAndPushDeviceIdIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            NotificationKind kind, Instant start, Instant end);

    // 보관 기간 지난 긴급 알림 기록 정리 (묶인 기사·보류 푸시는 DB 가 함께 지움)
    @Modifying
    @Query(nativeQuery = true, value = "delete from notification where kind in ('ALERT', 'DIGEST') and created_at < :createdBefore")
    int deleteAlertsCreatedBefore(@Param("createdBefore") Instant createdBefore);
}
