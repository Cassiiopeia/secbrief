package kr.suhsaechan.secbrief.advisory.catalog;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 분류 태그 스냅샷 제공: 30초마다 DB 에서 다시 읽음, 관리자 변경 직후에는 invalidate 로 바로
 * 분류·관심 판정·API 가 모두 이 스냅샷 하나를 씀 (배포 없이 새 항목 반영)
 */
@Component
@RequiredArgsConstructor
public class TagCatalogProvider {

    // 다시 읽는 간격 (설정 저장소와 같은 30초)
    static final Duration RELOAD_INTERVAL = Duration.ofSeconds(30);

    private final CatalogTagRepository catalogTagRepository;
    private final Clock clock;

    private volatile TagCatalog cachedCatalog;
    private volatile Instant loadedAt = Instant.MIN;

    public TagCatalog getCatalog() {
        Instant now = clock.instant();
        TagCatalog catalog = cachedCatalog;
        if (catalog == null || loadedAt.plus(RELOAD_INTERVAL).isBefore(now)) {
            catalog = load();
            cachedCatalog = catalog;
            loadedAt = now;
        }
        return catalog;
    }

    /** 다음 조회 때 DB 에서 다시 읽게 함 (관리자 변경 뒤) */
    public void invalidate() {
        cachedCatalog = null;
    }

    private TagCatalog load() {
        return TagCatalog.of(catalogTagRepository.findAllByOrderByKindAscDisplayOrderAscCodeAsc().stream()
                .filter(CatalogTag::isVisible)
                .map(CatalogEntry::from)
                .toList());
    }
}
