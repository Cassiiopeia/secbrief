package kr.suhsaechan.secbrief.advisory.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.support.CatalogSeedFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 분류 규칙 (운영 시드 카탈로그 + 운영 DB 에서 읽기만 해서 고른 실제 제목·제목 속 이름·패키지 값, 2026-11-27)
 * 순서: CPE·KEV 제조사 > 패키지 > 제목 속 이름 > 제목, 제품 > 분야 낱말 > AI 분야 > other
 */
class ArticleClassifierTest {

    private static final TagCatalog CATALOG = CatalogSeedFixtures.seedCatalog();

    @ParameterizedTest(name = "{1} > {3}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            NVD                | CVE-2026-135578: Sandbox escape due to use-after-free in the Machine Learning: On Device component. This vulnerability was fixed in Firef… | Firefox | browser | firefox
            CISA_KEV           | CVE-2026-76418: Cisco Secure Firewall ASA Software and Cisco Secure FTD Software Unchecked Return Value to NULL Pointer Dereference Vulnerability | Cisco | network | cisco
            CISA_KEV           | CVE-2026-40217: BerriAI LiteLLM Unprotected Alternate Channel Vulnerability | BerriAI LiteLLM | ai-tool | -
            CISA_KEV           | CVE-2026-48356: Adobe Commerce and Magento Unrestricted Upload of File with Dangerous Type Vulnerability | Adobe Commerce | web-app | -
            NVD                | CVE-2026-116439: Dell PowerStore SDNAS contains a path traversal vulnerability that could allow an unauthenticated attacker to read, modi… | Dell PowerStore SDNAS | cloud | -
            NVD                | CVE-2026-123890: VMware vCenter contains an authentication bypass vulnerability | VMware vCenter | cloud | vmware
            KISA               | Splunk 제품 보안 업데이트 권고 | Splunk | it-ops | splunk
            KISA               | ConnectWise 제품 보안 업데이트 권고 | ConnectWise | it-ops | -
            KISA_VULNERABILITY | CVE-2026-76143 지니언스 Genian SSL PNS 2차 인증 우회 | 지니언스 | network | -
            REDHAT_RHSA        | RHSA-2026:102402: Important: tigervnc security update | tigervnc | os | -
            REDHAT_RHSA        | RHSA-2026:103484: Important: raptor2 security update | RHSA-2026:103484 | os | -
            DEBIAN_DSA         | DSA-6652-1 linux - security update | linux | os | -
            UBUNTU_USN         | USN-9190-2: Linux kernel (HWE) vulnerabilities | Linux | os | linux-kernel
            GHSA               | Mustache for Ruby: RCE using built-in method access | Mustache for Ruby | web-server | -
            NVD                | CVE-2026-133336: Joomla! extension - chronoengine ChronoForms SQL injection | Joomla | web-app | -
            """)
    void classifiesRealTitlesByProductThenFieldTerms(String sourceCode, String title, String subjectName,
                                                     String expectedFieldCode, String expectedProductCode) {
        ArticleClassification classification = ArticleClassifier.classify(
                ArticleClassificationInput.of(sourceCode, title, subjectName, List.of(), null), CATALOG);

        assertThat(classification.fieldCode()).isEqualTo(expectedFieldCode);
        assertThat(classification.productCode()).isEqualTo(expectedProductCode);
        assertThat(classification.ecosystemCode()).isNull();
    }

    @Test
    void readsEcosystemAndPackageFromGithubAdvisoryFacts() {
        ArticleClassification budibase = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.GHSA_HIGH,
                "Budibase: Unauthenticated global-admin & database creation via tenant-confusion bypass", "Budibase",
                List.of(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "@budibase/server (npm)")), null), CATALOG);
        ArticleClassification flyto = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.GHSA_HIGH,
                "Flyto2 Core: SSRF guard validates the resolved IP but connects by hostname", "Flyto2 Core",
                List.of(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "flyto-core (pip)")), "web-app"), CATALOG);
        ArticleClassification unknownEcosystem = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.GHSA,
                "Example: flaw", null, List.of(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "example (swift)")), null),
                CATALOG);

        assertThat(budibase).isEqualTo(new ArticleClassification("web-app", "npm", null, "@budibase/server",
                ClassificationSource.KEYWORD));
        // 규칙이 못 정하면 AI 가 고른 분야
        assertThat(flyto).isEqualTo(new ArticleClassification("web-app", "pypi", null, "flyto-core", ClassificationSource.AI));
        // 카탈로그에 없는 생태계는 그 밖
        assertThat(unknownEcosystem.ecosystemCode()).isEqualTo("other");
    }

    @Test
    void prefersCpeAndKevVendorProductOverTitleWords() {
        ArticleClassification nvd = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.NVD,
                "CVE-2026-1: Heap overflow in the SSL VPN daemon allows remote code execution", null,
                List.of(new SourceFact(SourceFactLabel.AFFECTED_PRODUCT, "fortinet fortios, fortinet fortiproxy")), null),
                CATALOG);
        ArticleClassification kev = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.CISA_KEV,
                "CVE-2026-2: Improper Authentication Vulnerability", null,
                List.of(new SourceFact(SourceFactLabel.VENDOR_PRODUCT, "Ivanti Endpoint Manager Mobile (EPMM)")), null),
                CATALOG);

        assertThat(nvd.productCode()).isEqualTo("fortinet");
        assertThat(nvd.fieldCode()).isEqualTo("network");
        assertThat(nvd.fieldSource()).isEqualTo(ClassificationSource.PRODUCT);
        assertThat(kev.productCode()).isEqualTo("ivanti");
    }

    @Test
    void doesNotMatchWordInsideAnotherWordAndKeepsLongestMatch() {
        // git 은 github 에 맞지 않음, "Cisco IOS XE" 는 iOS 가 아니라 Cisco (긴 낱말 우선)
        ArticleClassification github = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.GHSA,
                "Example GitHub Action leaks tokens", null, List.of(), null), CATALOG);
        ArticleClassification ciscoIos = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.NVD,
                "CVE-2026-3: Cisco IOS XE Software Web UI privilege escalation", null, List.of(), null), CATALOG);

        assertThat(github.productCode()).isNull();
        assertThat(ciscoIos.productCode()).isEqualTo("cisco");
    }

    @Test
    void leavesAlertLevelAndReportsUnclassifiedAndFallsBackToOther() {
        assertThat(ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.KISA_ALERT_LEVEL,
                "사이버위기 경보 단계 '관심' → '주의' 상향 조정", null, List.of(), "os"), CATALOG))
                .isEqualTo(ArticleClassification.notClassified());
        assertThat(ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.KISA_REPORT,
                "AI 에이전트·업무 자동화 도구 보안수칙", null, List.of(), null), CATALOG).fieldCode()).isNull();

        ArticleClassification other = ArticleClassifier.classify(ArticleClassificationInput.of(SourceCode.NVD,
                "CVE-2026-4: Thinkwise buffer overflow", "Thinkwise", List.of(), "removed-field"), CATALOG);
        // 카탈로그에 없는 AI 분야는 버리고 그 밖
        assertThat(other.fieldCode()).isEqualTo("other");
        assertThat(other.fieldSource()).isEqualTo(ClassificationSource.NONE);
    }
}
