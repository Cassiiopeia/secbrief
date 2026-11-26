package kr.suhsaechan.secbrief.advisory.policy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.CyberCrisisLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 경보단계 제목 > 단계 변경: KISA 경보단계 게시판 전체 69건(운영 DB 10건 포함)의 바뀌기 전·후 단계
 * 상향·하향·환원·발령 표현, 따옴표 종류(' ‘’), 따옴표 없는 단계 이름, 5단계 밖 이름('경고')은 모름
 */
class CyberCrisisLevelTitleParserTest {

    private static final String NOT_IN_TITLE = "-";

    @Test
    void parsesEveryPublishedAlertLevelTitle() {
        List<String[]> rows = readTitleFixture();

        assertThat(rows).hasSize(69);
        for (String[] row : rows) {
            String title = row[3];
            Optional<CyberCrisisLevelChange> change = CyberCrisisLevelTitleParser.parse(title);
            if (NOT_IN_TITLE.equals(row[2])) {
                assertThat(change).as(title).isEmpty();
                continue;
            }
            assertThat(change).as(title).isPresent();
            assertThat(change.get().toLevel()).as(title).isEqualTo(toLevel(row[2]));
            assertThat(change.get().fromLevel()).as(title).isEqualTo(NOT_IN_TITLE.equals(row[1]) ? null : toLevel(row[1]));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // 운영 DB 의 10건 중 표현이 다른 것
            "사이버위기 경보 단계 '관심' → '주의' 상향 조정|ATTENTION|CAUTION",
            "사이버위기 경보 단계 '주의' → '관심'으로 하향 조정|CAUTION|ATTENTION",
            "국내·외 사이버 위협 우려가 고조됨에 따라 '주의' 경보 발령||CAUTION",
            "민간분야 사이버위기 경보단계 '관심'에서 '정상'으로 환원|ATTENTION|NORMAL",
            // 따옴표 없음, 화살표 괄호, 정상회의 같은 낱말 안의 단계 이름
            "사이버위기 경보단계 주의 에서 정상 단계로 환원|CAUTION|NORMAL",
            "7.7 DDoS공격 소멸에 따른 경보단계 조정(관심 → 정상)|ATTENTION|NORMAL",
            "핵안보정상회의 관련 사이버공격 대비 ‘관심’ 경보 발령||ATTENTION",
            "가상 공지 '경계' → '심각' 상향 조정|ALERT|SEVERE"
    })
    void parsesWordingVariants(String title, CyberCrisisLevel fromLevel, CyberCrisisLevel toLevel) {
        assertThat(CyberCrisisLevelTitleParser.parse(title)).contains(new CyberCrisisLevelChange(fromLevel, toLevel));
    }

    @Test
    void returnsEmptyForTitleWithoutKnownLevel() {
        assertThat(CyberCrisisLevelTitleParser.parse("국가ㆍ공공기관 해킹 위협 증대에 따라 '경고' 단계 발령")).isEmpty();
        assertThat(CyberCrisisLevelTitleParser.parse(null)).isEmpty();
    }

    private static CyberCrisisLevel toLevel(String koreanName) {
        return CyberCrisisLevel.fromKoreanName(koreanName).orElseThrow();
    }

    // 게시일|바뀌기 전|바뀐 단계|제목 (# 줄은 설명)
    private static List<String[]> readTitleFixture() {
        try (InputStream stream = CyberCrisisLevelTitleParserTest.class.getResourceAsStream("/fixtures/kisa-board/alert-level-titles.psv")) {
            return new String(stream.readAllBytes(), UTF_8).lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\\|", 4))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
