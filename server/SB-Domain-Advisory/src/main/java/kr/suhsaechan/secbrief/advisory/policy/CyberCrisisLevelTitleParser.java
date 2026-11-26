package kr.suhsaechan.secbrief.advisory.policy;

import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;

/**
 * KISA 경보단계 공지 제목 > 단계 변경: 마지막에 나온 단계 이름 = 바뀐 단계, 그 앞 단계와 화살표·'에서' 사이면 바뀌기 전 단계
 * 따옴표 친 단계 이름 우선, 없으면 낱말로 쓴 단계 이름 (예: 주의 에서 정상 단계로 환원), 5단계 밖 이름('경고')은 모름
 */
public final class CyberCrisisLevelTitleParser {

    private static final String LEVEL_NAMES = "정상|관심|주의|경계|심각";
    // 따옴표: ' ‘ ’ " “ ”
    private static final String QUOTE_CHARACTERS = "['\\u2018\\u2019\"\\u201C\\u201D]";
    private static final Pattern QUOTED_LEVEL =
            Pattern.compile(QUOTE_CHARACTERS + "\\s*(" + LEVEL_NAMES + ")\\s*" + QUOTE_CHARACTERS);
    // 낱말 경계: 앞은 한글이 아님 (정상회의 제외), 뒤는 공백·단계·경보·에서·으로·로·닫는 괄호·끝
    private static final Pattern BARE_LEVEL =
            Pattern.compile("(?<![가-힣])(" + LEVEL_NAMES + ")(?=\\s|단계|경보|에서|으로|로|\\)|$)");
    // 바뀌기 전 > 후 표시: → , -> , 에서
    private static final Pattern CHANGE_MARKER = Pattern.compile("→|->|에서");

    private CyberCrisisLevelTitleParser() {
    }

    /**
     * 제목 > 단계 변경 (단계 이름이 없으면 비어 있음)
     */
    public static Optional<CyberCrisisLevelChange> parse(String title) {
        if (title == null) {
            return Optional.empty();
        }
        List<MatchResult> levelMatches = findLevelMatches(title);
        if (levelMatches.isEmpty()) {
            return Optional.empty();
        }
        MatchResult toMatch = levelMatches.getLast();
        CyberCrisisLevel toLevel = toLevel(toMatch);
        CyberCrisisLevel fromLevel = null;
        if (levelMatches.size() >= 2) {
            MatchResult fromMatch = levelMatches.get(levelMatches.size() - 2);
            String textBetween = title.substring(fromMatch.end(), toMatch.start());
            fromLevel = CHANGE_MARKER.matcher(textBetween).find() ? toLevel(fromMatch) : null;
        }
        return Optional.of(new CyberCrisisLevelChange(fromLevel, toLevel));
    }

    private static List<MatchResult> findLevelMatches(String title) {
        List<MatchResult> quotedMatches = QUOTED_LEVEL.matcher(title).results().toList();
        return quotedMatches.isEmpty() ? BARE_LEVEL.matcher(title).results().toList() : quotedMatches;
    }

    // 두 패턴 모두 1번 묶음이 단계 이름 (LEVEL_NAMES 안의 값이라 항상 있음)
    private static CyberCrisisLevel toLevel(MatchResult levelMatch) {
        return CyberCrisisLevel.fromKoreanName(levelMatch.group(1)).orElseThrow();
    }
}
