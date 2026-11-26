package kr.suhsaechan.secbrief.member.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import kr.suhsaechan.secbrief.member.constant.InterestKind;

/**
 * 기기 관심 기술 묶음: 제품·분야·생태계 code 목록 (모르는 code 는 판정 때 맞지 않아 자연히 무시됨)
 */
public record AlertInterests(Set<String> productCodes, Set<String> fieldCodes, Set<String> ecosystemCodes) {

    public AlertInterests {
        productCodes = productCodes == null ? Set.of() : Set.copyOf(productCodes);
        fieldCodes = fieldCodes == null ? Set.of() : Set.copyOf(fieldCodes);
        ecosystemCodes = ecosystemCodes == null ? Set.of() : Set.copyOf(ecosystemCodes);
    }

    public static AlertInterests none() {
        return new AlertInterests(Set.of(), Set.of(), Set.of());
    }

    /** 저장 행 > 묶음 */
    public static AlertInterests from(Set<PushDeviceInterest> interests) {
        Set<String> productCodes = new LinkedHashSet<>();
        Set<String> fieldCodes = new LinkedHashSet<>();
        Set<String> ecosystemCodes = new LinkedHashSet<>();
        for (PushDeviceInterest interest : interests) {
            switch (interest.getKind()) {
                case PRODUCT -> productCodes.add(interest.getCode());
                case FIELD -> fieldCodes.add(interest.getCode());
                case ECOSYSTEM -> ecosystemCodes.add(interest.getCode());
            }
        }
        return new AlertInterests(productCodes, fieldCodes, ecosystemCodes);
    }

    /** 묶음 > 저장 행 */
    public Set<PushDeviceInterest> toPushDeviceInterests() {
        Set<PushDeviceInterest> interests = new LinkedHashSet<>();
        productCodes.forEach(code -> interests.add(new PushDeviceInterest(InterestKind.PRODUCT, code)));
        fieldCodes.forEach(code -> interests.add(new PushDeviceInterest(InterestKind.FIELD, code)));
        ecosystemCodes.forEach(code -> interests.add(new PushDeviceInterest(InterestKind.ECOSYSTEM, code)));
        return interests;
    }

    public boolean isEmpty() {
        return productCodes.isEmpty() && fieldCodes.isEmpty() && ecosystemCodes.isEmpty();
    }

    public int size() {
        return productCodes.size() + fieldCodes.size() + ecosystemCodes.size();
    }
}
