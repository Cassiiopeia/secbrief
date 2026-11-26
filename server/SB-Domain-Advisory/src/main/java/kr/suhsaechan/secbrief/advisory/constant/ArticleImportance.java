package kr.suhsaechan.secbrief.advisory.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기사 중요도 3단계 (선언 순서 = 높은 순): 서버 이름(UPPER) <-> AI JSON 값(lower)
 * HEADLINE 널리 쓰는 제품의 악용·대규모 사고·국가 경보, NOTABLE 주목할 소식, ROUTINE 그 밖(기본값)
 */
@Getter
@RequiredArgsConstructor
public enum ArticleImportance {
    HEADLINE("headline"),
    NOTABLE("notable"),
    ROUTINE("routine");

    private final String apiValue;

    /**
     * AI JSON 값 > 중요도 (3단계 밖의 값은 빈 값)
     */
    public static Optional<ArticleImportance> fromApiValue(String apiValue) {
        return Arrays.stream(values())
                .filter(importance -> importance.apiValue.equals(apiValue))
                .findFirst();
    }

    /** 둘 중 높은 쪽 */
    public static ArticleImportance higherOf(ArticleImportance first, ArticleImportance second) {
        return first.ordinal() <= second.ordinal() ? first : second;
    }

    /** 이 단계 이상인 단계 목록 (자신 포함, 조회 조건용) */
    public List<ArticleImportance> toLevelsAtLeast() {
        return Arrays.stream(values()).filter(importance -> importance.ordinal() <= ordinal()).toList();
    }
}
