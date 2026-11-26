package kr.suhsaechan.secbrief.advisory.catalog;

import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogTagRepository extends JpaRepository<CatalogTag, Long> {

    // 종류 > 순서 > code 순 (관리자 화면·스냅샷)
    List<CatalogTag> findAllByOrderByKindAscDisplayOrderAscCodeAsc();

    Optional<CatalogTag> findByKindAndCode(CatalogTagKind kind, String code);
}
