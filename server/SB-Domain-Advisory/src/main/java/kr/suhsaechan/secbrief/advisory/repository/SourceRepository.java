package kr.suhsaechan.secbrief.advisory.repository;

import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findAllByIsEnabledTrueOrderByIdAsc();

    Optional<Source> findByCode(String code);
}
