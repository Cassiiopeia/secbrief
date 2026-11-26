package kr.suhsaechan.secbrief.member.repository;

import java.util.List;
import kr.suhsaechan.secbrief.member.entity.HeldPush;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HeldPushRepository extends JpaRepository<HeldPush, Long> {

    // 보류 푸시 전체: 기기(방해 금지 시간 포함)까지 한 번에, 기기별 보류 순서대로
    @Query("select h from HeldPush h join fetch h.pushDevice order by h.pushDevice.id asc, h.id asc")
    List<HeldPush> findAllWithPushDevice(Limit limit);
}
