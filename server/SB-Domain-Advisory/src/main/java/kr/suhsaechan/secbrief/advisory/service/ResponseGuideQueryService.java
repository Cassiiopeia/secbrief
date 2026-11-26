package kr.suhsaechan.secbrief.advisory.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideMatchReason;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideSection;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideItemResponse;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideSectionResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.RelatedArticlePolicy;
import kr.suhsaechan.secbrief.advisory.policy.ResponseGuidePolicy;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.ResponseGuideCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대응 모아보기: 후보 조회 1회(같은 CVE > 같은 제품 > 같은 제조사 공지) > 기사 묶음 조회 > 구역 판정·걸러내기 > 구역별 최대 5건
 * 구역 판정은 ResponseGuidePolicy.toSection 한 곳, 빈 구역은 응답에서 뺌
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResponseGuideQueryService {

    // 공식 대응 정렬: 묶인 이유 순 > 같은 이유 안에서 KISA 먼저
    private static final Comparator<ResponseGuideItemResponse> KISA_FIRST_WITHIN_MATCH_REASON =
            Comparator.comparing(ResponseGuideItemResponse::matchReason)
                    .thenComparing(item -> SourceCode.isKisa(item.article().sourceCode()) ? 0 : 1);

    private final ArticleRepository articleRepository;

    public List<ResponseGuideSectionResponse> findResponseGuideSections(Article article) {
        List<String> viewedCveIds = RelatedArticlePolicy.findCveIds(article.getCveId(), article.getTitle());
        List<ResponseGuideCandidate> candidates = findCandidates(article);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Article> candidateArticleById = articleRepository.findAllById(
                        candidates.stream().map(ResponseGuideCandidate::getArticleId).toList()).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));

        // 후보 > 구역별 항목 (조회 순서 유지, 구역 밖·걸러낸 후보는 뺌)
        Map<ResponseGuideSection, List<ResponseGuideItemResponse>> itemsBySection = new EnumMap<>(ResponseGuideSection.class);
        for (ResponseGuideCandidate candidate : candidates) {
            Article candidateArticle = candidateArticleById.get(candidate.getArticleId());
            if (candidateArticle == null) {
                continue;
            }
            ResponseGuideMatchReason matchReason = ResponseGuideMatchReason.fromMatchRank(candidate.getMatchRank());
            Optional<ResponseGuideSection> section = ResponseGuidePolicy.toSection(
                    candidateArticle.getSource().getCode(), candidateArticle.getSource().getKind());
            boolean hasCandidateCveIds = !RelatedArticlePolicy.findCveIds(
                    candidateArticle.getCveId(), candidateArticle.getTitle()).isEmpty();
            if (section.isEmpty() || !ResponseGuidePolicy.isAcceptedCandidate(
                    !viewedCveIds.isEmpty(), hasCandidateCveIds, section.get(), matchReason)) {
                continue;
            }
            String matchedCveId = matchReason == ResponseGuideMatchReason.SAME_CVE
                    ? RelatedArticlePolicy.findSharedCveId(viewedCveIds, candidateArticle.getCveId(), candidateArticle.getTitle())
                    : null;
            itemsBySection.computeIfAbsent(section.get(), ignored -> new ArrayList<>())
                    .add(ResponseGuideItemResponse.of(candidateArticle, matchReason, matchedCveId));
        }
        return toSectionResponses(itemsBySection);
    }

    private List<ResponseGuideCandidate> findCandidates(Article article) {
        boolean isViewedVendorNotice = ResponseGuidePolicy.isVendorNoticeSource(article.getSource().getCode());
        return articleRepository.findResponseGuideCandidates(
                article.getId(),
                RelatedArticlePolicy.toCveIdPattern(article.getCveId(), article.getTitle()),
                article.getPublishedAt().minus(ResponseGuidePolicy.CVE_MATCH_LOOKBACK),
                ResponseGuidePolicy.toProductMatchName(article),
                article.getPublishedAt().minus(ResponseGuidePolicy.PRODUCT_MATCH_WINDOW),
                article.getPublishedAt().plus(ResponseGuidePolicy.PRODUCT_MATCH_WINDOW),
                ResponseGuidePolicy.PRODUCT_MATCH_EXCLUDED_TOPICS.stream().map(ArticleTopic::name).toList(),
                ResponseGuidePolicy.VENDOR_NOTICE_SOURCE_CODE,
                isViewedVendorNotice,
                ResponseGuidePolicy.MAXIMUM_CANDIDATE_COUNT);
    }

    // 구역 순서대로 (정렬은 안정적이라 같은 순위 안에서는 조회 순서 유지)
    private static List<ResponseGuideSectionResponse> toSectionResponses(
            Map<ResponseGuideSection, List<ResponseGuideItemResponse>> itemsBySection) {
        List<ResponseGuideSectionResponse> sectionResponses = new ArrayList<>();
        for (ResponseGuideSection section : ResponseGuideSection.values()) {
            List<ResponseGuideItemResponse> items = itemsBySection.getOrDefault(section, List.of());
            if (items.isEmpty()) {
                continue;
            }
            List<ResponseGuideItemResponse> orderedItems = section == ResponseGuideSection.OFFICIAL
                    ? items.stream().sorted(KISA_FIRST_WITHIN_MATCH_REASON).toList()
                    : items;
            sectionResponses.add(new ResponseGuideSectionResponse(section, items.size(),
                    orderedItems.stream().limit(ResponseGuidePolicy.MAXIMUM_ITEM_COUNT_PER_SECTION).toList()));
        }
        return sectionResponses;
    }
}
