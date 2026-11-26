package kr.suhsaechan.secbrief.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import kr.suhsaechan.secbrief.advisory.classification.ArticleClassificationService;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.member.constant.PushPlatform;
import kr.suhsaechan.secbrief.member.entity.Notification;
import kr.suhsaechan.secbrief.member.entity.NotificationArticle;
import kr.suhsaechan.secbrief.member.entity.PushDevice;
import kr.suhsaechan.secbrief.member.repository.NotificationRepository;
import kr.suhsaechan.secbrief.member.repository.PushDeviceRepository;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * #93 API (DB): 응답 모양이 앱과 함께 쓰는 계약 예시(src/test/resources/contract/*.json)와 같은지 (값이 아니라 키),
 * 카탈로그 ETag·304, 분류 조건 목록, 관심 기술 저장 때 없는 code 버림
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterestApiContractTest extends PostgresContainerTestSupport {

    private static final String PUSH_TOKEN = "ExponentPushToken[contract]";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private PushDeviceRepository pushDeviceRepository;
    @Autowired
    private ArticleClassificationService articleClassificationService;

    private Article fortinetArticle;
    private Article npmArticle;

    @BeforeEach
    void saveClassifiedArticles() {
        notificationRepository.deleteAll();
        pushDeviceRepository.deleteAll();
        articleRepository.deleteAll();
        fortinetArticle = saveArticle(SourceCode.NVD, "CVE-2026-9401: Fortinet FortiOS SSL-VPN heap overflow", "9.8",
                ArticleImportance.NOTABLE, List.of(), Instant.now());
        npmArticle = saveArticle(SourceCode.GHSA_HIGH, "Budibase: Unauthenticated global-admin creation", "8.2",
                ArticleImportance.ROUTINE, List.of(new SourceFact(SourceFactLabel.AFFECTED_PACKAGE, "@budibase/server (npm)")),
                Instant.now());
        // 어제 수집분 (어제 기준 알림 추정)
        saveArticle(SourceCode.NVD, "CVE-2026-9403: Fortinet FortiWeb path traversal", "9.6", ArticleImportance.NOTABLE,
                List.of(), Instant.now().minus(Duration.ofDays(1)));
    }

    @Test
    void servesCatalogWithEtagAndNotModified() throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/catalog/tags"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        assertSameShape(data, readContract("catalog-tags.json"));

        mockMvc.perform(get("/api/v1/catalog/tags").header("If-None-Match", "\"" + data.path("version").asText() + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void countsTodayNewArticlesByFieldAndEcosystem() throws Exception {
        JsonNode data = getData("/api/v1/articles/new-counts?timeZone=Asia/Seoul");

        assertSameShape(data, readContract("new-counts.json"));
        assertThat(data.path("totalCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(data.path("ecosystemCounts").get(0).path("code").asText()).isEqualTo("npm");
        mockMvc.perform(get("/api/v1/articles/new-counts?timeZone=Mars/Base"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIME_ZONE"));
    }

    @Test
    void filtersListByTagsNotableInterestsAndCollectedSince() throws Exception {
        assertThat(listIds("/api/v1/articles?ecosystemCode=npm")).containsExactly(npmArticle.getId());
        assertThat(listIds("/api/v1/articles?fieldCode=network&isNotable=true")).contains(fortinetArticle.getId())
                .doesNotContain(npmArticle.getId());
        assertThat(listIds("/api/v1/articles?interestProductCodes=fortinet&interestEcosystemCodes=npm"
                + "&collectedSince=" + Instant.now().minus(Duration.ofHours(1))))
                .containsExactlyInAnyOrder(fortinetArticle.getId(), npmArticle.getId());
        assertThat(listIds("/api/v1/articles?sourceCodes=GHSA,GHSA_HIGH")).containsExactly(npmArticle.getId());
        mockMvc.perform(get("/api/v1/articles?ecosystemCode=npm"))
                .andExpect(jsonPath("$.data.items[0].packageName").value("@budibase/server"))
                .andExpect(jsonPath("$.data.items[0].isNotable").value(false));
    }

    @Test
    void estimatesYesterdayAlertsWithContractShape() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/push-devices/alert-estimate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledAlertCategories":["KEV","KISA_URGENT"],
                                 "interests":{"productCodes":["fortinet"],"fieldCodes":[],"ecosystemCodes":[]},
                                 "digest":{"deliveryMode":"DIGEST","digestTime":"08:00"},"timeZone":"Asia/Seoul"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(responseBody).path("data");

        assertSameShape(data, readContract("alert-estimate.json"));
    }

    @Test
    void savesOnlyKnownInterestCodes() throws Exception {
        mockMvc.perform(put("/api/v1/push-devices").contentType(MediaType.APPLICATION_JSON).content("""
                        {"pushToken":"%s","platform":"IOS","enabledAlertCategories":[],
                         "interests":{"productCodes":["fortinet","removed-product"],"fieldCodes":["network"],"ecosystemCodes":[]},
                         "digest":{"deliveryMode":"IMMEDIATE","digestTime":"07:30"}}""".formatted(PUSH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interests.productCodes.length()").value(1))
                .andExpect(jsonPath("$.data.interests.productCodes[0]").value("fortinet"))
                .andExpect(jsonPath("$.data.digest.deliveryMode").value("IMMEDIATE"))
                .andExpect(jsonPath("$.data.digest.digestTime").value("07:30"));
        assertThat(pushDeviceRepository.findByPushToken(PUSH_TOKEN)).isPresent();
    }

    @Test
    void returnsDigestDetailWithContractShape() throws Exception {
        long pushDeviceId = pushDeviceRepository.save(PushDevice.create(PUSH_TOKEN, PushPlatform.IOS,
                EnumSet.of(AlertCategory.KEV))).getId();
        Notification digest = notificationRepository.save(Notification.createForDevice(NotificationKind.DIGEST, pushDeviceId,
                AlertCategory.INTEREST, "오늘 아침 요약: 관심 분야 소식 2건", "네트워크 장비 1 · 웹 앱·CMS 1", List.of(
                        new NotificationArticle(0, fortinetArticle.getId(), AlertCategory.INTEREST),
                        new NotificationArticle(1, npmArticle.getId(), AlertCategory.INTEREST)), Instant.now()));

        JsonNode data = getData("/api/v1/notifications/" + digest.getId());

        assertSameShape(data, readContract("digest-notification.json"));
        assertThat(data.path("digest").path("fieldSections")).hasSize(2);
    }

    private Article saveArticle(String sourceCode, String title, String cvssScore, ArticleImportance importance,
                                List<SourceFact> sourceFacts, Instant collectedAt) {
        Source source = sourceRepository.findByCode(sourceCode).orElseThrow();
        BigDecimal score = new BigDecimal(cvssScore);
        Article article = Article.create(source, new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, title,
                "https://example.test/" + System.nanoTime(), source.getDisplayName(), collectedAt, false, null, score, "3.1",
                false, score.compareTo(new BigDecimal("9.0")) >= 0, null, null, sourceFacts), collectedAt);
        article.applyAiAnalysis(ArticleTopic.VULNERABILITY, null, importance, null);
        articleClassificationService.classify(article);
        return articleRepository.save(article);
    }

    private JsonNode getData(String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data");
    }

    private List<Long> listIds(String path) throws Exception {
        List<Long> ids = new ArrayList<>();
        getData(path).path("items").forEach(item -> ids.add(item.path("id").asLong()));
        return ids;
    }

    private JsonNode readContract(String fileName) throws IOException {
        try (InputStream contract = getClass().getResourceAsStream("/contract/" + fileName)) {
            return objectMapper.readTree(contract);
        }
    }

    // 키 모양 비교: 객체는 키 집합이 같아야 함, 배열은 둘 다 비지 않았으면 첫 원소끼리 (값은 비교하지 않음)
    private static void assertSameShape(JsonNode actual, JsonNode contract) {
        assertSameShape(actual, contract, "$");
    }

    private static void assertSameShape(JsonNode actual, JsonNode contract, String path) {
        if (contract.isObject()) {
            assertThat(new TreeSet<>(toFieldNames(actual))).as(path).isEqualTo(new TreeSet<>(toFieldNames(contract)));
            contract.fieldNames().forEachRemaining(name -> {
                if (!contract.path(name).isNull() && !actual.path(name).isNull()) {
                    assertSameShape(actual.path(name), contract.path(name), path + "." + name);
                }
            });
        } else if (contract.isArray() && !contract.isEmpty() && !actual.isEmpty()) {
            assertSameShape(actual.get(0), contract.get(0), path + "[0]");
        }
    }

    private static List<String> toFieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
