package kr.suhsaechan.secbrief.advisory.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 분류 낱말 일치: 영문 낱말 경계, 한글 부분 일치, 약한 낱말 분리, 가장 긴 일치, 생태계 원문 값 일치
 */
class CatalogTermMatcherTest {

    @Test
    void matchesWholeWordsAndHangulInsideWords() {
        CatalogTermMatcher matcher = CatalogTermMatcher.of("git, git lfs, 한컴, ~rhsa");

        assertThat(matcher.findLongestStrongMatchLength("git lfs: crafted ssh url")).isEqualTo("git lfs".length());
        assertThat(matcher.findLongestStrongMatchLength("github actions")).isZero();
        assertThat(matcher.findLongestStrongMatchLength("한컴오피스 보안 업데이트")).isEqualTo(2);
        // 약한 낱말은 강한 낱말에 섞이지 않음
        assertThat(matcher.findLongestStrongMatchLength("rhsa-2026:1: kernel")).isZero();
        assertThat(matcher.findLongestWeakMatchLength("rhsa-2026:1: kernel")).isEqualTo(4);
    }

    @Test
    void comparesEcosystemValuesExactlyIgnoringCase() {
        CatalogTermMatcher matcher = CatalogTermMatcher.of("composer, packagist");

        assertThat(matcher.hasExactTerm(" Composer ")).isTrue();
        assertThat(matcher.hasExactTerm("compose")).isFalse();
        assertThat(matcher.hasExactTerm(null)).isFalse();
        assertThat(CatalogTermMatcher.of("").isEmpty()).isTrue();
    }
}
