package kr.suhsaechan.secbrief.member.repository;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.member.entity.PushTicket;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushTicketRepository extends JpaRepository<PushTicket, Long> {

    // 접수증 확인 대상: 발송 후 대기 시간이 지난 티켓 (오래된 것부터 limit 건)
    @Query("select t from PushTicket t join fetch t.pushDevice where t.sentAt <= :sentBefore order by t.id asc")
    List<PushTicket> findReceiptCheckTargets(@Param("sentBefore") Instant sentBefore, Limit limit);

    // 접수증 보관 기간이 지난 티켓 정리 (더는 확인할 수 없음)
    @Modifying
    @Query("delete from PushTicket t where t.sentAt <= :sentBefore")
    int deleteAllSentBefore(@Param("sentBefore") Instant sentBefore);
}
