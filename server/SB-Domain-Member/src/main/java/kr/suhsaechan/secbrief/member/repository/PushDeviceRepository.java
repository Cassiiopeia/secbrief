package kr.suhsaechan.secbrief.member.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByPushToken(String pushToken);

    List<PushDevice> findAllByPushTokenIn(Collection<String> pushTokens);

    // 발송 대상 전체: 켠 종류까지 한 번에 (기기마다 따로 조회하지 않게)
    @Query("select distinct d from PushDevice d join fetch d.enabledAlertCategories")
    List<PushDevice> findAllWithEnabledAlertCategories();
}
