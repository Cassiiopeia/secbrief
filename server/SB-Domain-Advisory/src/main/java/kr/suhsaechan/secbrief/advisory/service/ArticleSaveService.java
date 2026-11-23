package kr.suhsaechan.secbrief.advisory.service;

import java.time.Instant;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기사 저장: CVE 가 같으면 한 행으로 병합 (NVD 가 링크 원본, KEV 표시 유지), 그 밖은 원문 링크로 중복 제거
 */
@Service
@RequiredArgsConstructor
public class ArticleSaveService {

    private final ArticleRepository articleRepository;

    /**
     * 초안 1건 저장: CVE 병합 > 링크 중복 확인 > 새로 저장
     */
    @Transactional
    public SaveOutcome saveOrMerge(Source source, ArticleDraft draft, Instant collectedAt) {
        if (draft.cveId() != null) {
            Optional<Article> sameCveArticle = articleRepository.findByCveId(draft.cveId());
            if (sameCveArticle.isPresent()) {
                return mergeIntoSameCve(sameCveArticle.get(), source, draft);
            }
        }
        if (articleRepository.existsByOriginalUrl(draft.originalUrl())) {
            return SaveOutcome.DUPLICATE;
        }
        articleRepository.save(Article.create(source, draft, collectedAt));
        return SaveOutcome.CREATED;
    }

    // 병합 규칙 3개: NVD 가 KEV·GHSA 행을 만나면 링크 교체, KEV 면 등재 표시, 긴급은 기존 || 새 값
    private SaveOutcome mergeIntoSameCve(Article existing, Source source, ArticleDraft draft) {
        boolean isChanged = false;
        if (isNvdSource(source) && !isNvdSource(existing.getSource())) {
            existing.replaceLinkWithNvd(source, draft);
            isChanged = true;
        }
        if (draft.isKev() && !existing.isKev()) {
            existing.markKev();
            isChanged = true;
        }
        if (draft.isUrgent() && !existing.isUrgent()) {
            existing.markUrgent();
            isChanged = true;
        }
        return isChanged ? SaveOutcome.MERGED : SaveOutcome.DUPLICATE;
    }

    private static boolean isNvdSource(Source source) {
        return SourceCode.NVD.equals(source.getCode());
    }
}
