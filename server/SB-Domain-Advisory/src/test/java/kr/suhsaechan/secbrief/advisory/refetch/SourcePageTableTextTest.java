package kr.suhsaechan.secbrief.advisory.refetch;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * 표 > 행 글: 머리글 짝짓기, rowspan·colspan 펼침, 머리글 없는 표, 빈 칸, 표 앞뒤 글 순서, 안쪽 표 (#57)
 * 표 모양은 KISA 보안공지·취약점 정보 게시글을 본뜬 가짜 값
 */
class SourcePageTableTextTest {

    @Test
    void pairsEveryCellWithItsHeader() {
        String rowText = toRowText("""
                <table><thead><tr><th>제품명</th><th>영향받는 버전</th><th>해결 버전</th></tr></thead>
                <tbody><tr><td>Example OS 10.2</td><td>10.2.7 미만</td><td>10.2.7 이상</td></tr>
                <tr><td>Example OS 11.1</td><td>11.1.4 미만</td><td>11.1.4 이상</td></tr></tbody></table>""");

        assertThat(rowText).isEqualTo("[제품명: Example OS 10.2 | 영향받는 버전: 10.2.7 미만 | 해결 버전: 10.2.7 이상] "
                + "[제품명: Example OS 11.1 | 영향받는 버전: 11.1.4 미만 | 해결 버전: 11.1.4 이상]");
    }

    @Test
    void repeatsRowSpannedProductOnEveryRow() {
        String rowText = toRowText("""
                <table><tr><th>제품명</th><th>영향받는 버전</th><th>해결 버전</th></tr>
                <tr><td rowspan="2">Example Server</td><td>5.0 ~ 5.2.2</td><td>5.2.3</td></tr>
                <tr><td>4.0 ~ 4.9.8</td><td>4.9.9</td></tr>
                <tr><td>Example Agent</td><td>2.1 미만</td><td>2.1</td></tr></table>""");

        assertThat(rowText).isEqualTo("[제품명: Example Server | 영향받는 버전: 5.0 ~ 5.2.2 | 해결 버전: 5.2.3] "
                + "[제품명: Example Server | 영향받는 버전: 4.0 ~ 4.9.8 | 해결 버전: 4.9.9] "
                + "[제품명: Example Agent | 영향받는 버전: 2.1 미만 | 해결 버전: 2.1]");
    }

    @Test
    void keepsColumnSpanOnFirstColumnAndSkipsEmptyCells() {
        String rowText = toRowText("""
                <table><tr><th>제품명</th><th>영향받는 버전</th><th>해결 버전</th></tr>
                <tr><td>Example Portal</td><td colspan="2">모든 버전 (지원 종료)</td></tr>
                <tr><td>Example App</td><td>3.1</td><td></td></tr></table>""");

        // 가로 병합 칸은 첫 열 머리글에만 (해결 버전 짝을 만들지 않음)
        assertThat(rowText).isEqualTo("[제품명: Example Portal | 영향받는 버전: 모든 버전 (지원 종료)] "
                + "[제품명: Example App | 영향받는 버전: 3.1]");
    }

    @Test
    void joinsCellsWithoutHeader() {
        assertThat(toRowText("<table><tr><td>Example Docs</td><td>10.0.0 미만</td></tr></table>"))
                .isEqualTo("[Example Docs | 10.0.0 미만]");
    }

    @Test
    void keepsTextOrderAroundTablesAndFlattensInnerTable() {
        Element container = Jsoup.parseBodyFragment("""
                <div><p>□ 설명</p><table><tr><th>CVE</th><th>CVSS</th></tr><tr><td>CVE-2026-0001</td><td>8.4</td></tr></table>
                <p>□ 영향받는 제품</p><table><tr><th>제품</th><th>비고</th></tr>
                <tr><td>Example NAC</td><td><table><tr><td>내부 표</td></tr></table></td></tr></table><p>□ 참고사이트</p></div>""")
                .selectFirst("div");

        SourcePageTableText.replaceTablesWithRowText(container);

        assertThat(container.text()).isEqualTo("□ 설명 [CVE: CVE-2026-0001 | CVSS: 8.4] □ 영향받는 제품 "
                + "[제품: Example NAC | 비고: 내부 표] □ 참고사이트");
    }

    private static String toRowText(String tableHtml) {
        return SourcePageTableText.toRowText(Jsoup.parseBodyFragment(tableHtml).selectFirst("table"));
    }
}
