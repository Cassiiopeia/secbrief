package kr.suhsaechan.secbrief.application.notification.settings;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.member.dto.InterestsRequest;
import kr.suhsaechan.secbrief.member.dto.InterestsResponse;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 알림 기기 설정 저장: 관심 기술을 지금 카탈로그에 있는 code 로만 거름 > 기기 등록 > 응답도 있는 code 만
 * 지운(숨긴) 항목 code 는 조용히 빠짐 (이름만 바뀐 항목은 code 가 같아 그대로)
 */
@Service
@RequiredArgsConstructor
public class AlertSettingsService {

    private final PushDeviceService pushDeviceService;
    private final TagCatalogProvider tagCatalogProvider;

    public PushDeviceResponse register(PushDeviceRegisterRequest request) {
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        PushDeviceRegisterRequest sanitizedRequest = request.interests() == null
                ? request
                : request.withInterests(toKnownInterests(request.interests(), catalog));
        PushDeviceResponse response = pushDeviceService.register(sanitizedRequest);
        InterestsResponse knownInterests = new InterestsResponse(
                filterKnown(response.interests().productCodes(), CatalogTagKind.PRODUCT, catalog).stream().sorted().toList(),
                filterKnown(response.interests().fieldCodes(), CatalogTagKind.FIELD, catalog).stream().sorted().toList(),
                filterKnown(response.interests().ecosystemCodes(), CatalogTagKind.ECOSYSTEM, catalog).stream().sorted().toList());
        return new PushDeviceResponse(response.enabledAlertCategories(), response.quietHours(), knownInterests,
                response.digest());
    }

    /** 관심 기술 요청 > 카탈로그에 있는 code 만 */
    public static InterestsRequest toKnownInterests(InterestsRequest interests, TagCatalog catalog) {
        return new InterestsRequest(filterKnown(interests.productCodes(), CatalogTagKind.PRODUCT, catalog),
                filterKnown(interests.fieldCodes(), CatalogTagKind.FIELD, catalog),
                filterKnown(interests.ecosystemCodes(), CatalogTagKind.ECOSYSTEM, catalog));
    }

    private static Set<String> filterKnown(Collection<String> codes, CatalogTagKind kind, TagCatalog catalog) {
        if (codes == null) {
            return Set.of();
        }
        return codes.stream().filter(code -> catalog.hasCode(kind, code)).collect(Collectors.toSet());
    }
}
