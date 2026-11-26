package kr.suhsaechan.secbrief.advisory.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기사 저장: CVE 가 같으면 한 행으로 병합 (NVD 가 링크 원본, KEV 표시 유지), 그 밖은 원문 링크로 중복 제거
 * 출처들이 동시에 수집하므로 건별 트랜잭션 + 유일 제약 위반 시 1회 재시도
 */
@Service
@RequiredArgsConstructor
public class ArticleSaveService {

    // IN 절 한 번에 넣는 링크 수 (KEV 첫 수집처럼 큰 목록은 나눠 조회)
    static final int ORIGINAL_URL_LOOKUP_CHUNK_SIZE = 500;

    private final ArticleRepository articleRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 초안 1건 저장: CVE 병합 > 같은 링크면 빠진 값만 채움 > 새로 저장 (건별 트랜잭션)
     * 유일 제약 위반이면 1회 재시도, 두 번째 위반은 그대로 던짐 (호출부가 중복으로 셈)
     */
    public SaveOutcome saveOrMerge(Source source, ArticleDraft draft, Instant collectedAt) {
        try {
            return saveOrMergeInNewTransaction(source, draft, collectedAt);
        } catch (DataIntegrityViolationException exception) {
            // 다른 출처가 같은 CVE·링크를 먼저 저장한 경우: 다시 조회하면 병합 경로를 타서 KEV 표시 등이 유실되지 않음
            return saveOrMergeInNewTransaction(source, draft, collectedAt);
        }
    }

    /**
     * 저장돼 있고 더 채울 것이 없는 원문 링크만 추림 (수집 묶음에서 건너뛸 것을 조회 몇 번으로 확인)
     */
    @Transactional(readOnly = true)
    public Set<String> findSettledOriginalUrls(Collection<String> originalUrls) {
        List<String> distinctOriginalUrls = new ArrayList<>(new HashSet<>(originalUrls));
        Set<String> existingOriginalUrls = new HashSet<>();
        for (int chunkStart = 0; chunkStart < distinctOriginalUrls.size(); chunkStart += ORIGINAL_URL_LOOKUP_CHUNK_SIZE) {
            int chunkEnd = Math.min(chunkStart + ORIGINAL_URL_LOOKUP_CHUNK_SIZE, distinctOriginalUrls.size());
            existingOriginalUrls.addAll(
                    articleRepository.findSettledOriginalUrls(distinctOriginalUrls.subList(chunkStart, chunkEnd)));
        }
        return existingOriginalUrls;
    }

    private SaveOutcome saveOrMergeInNewTransaction(Source source, ArticleDraft draft, Instant collectedAt) {
        return transactionTemplate.execute(status -> saveOrMergeOnce(source, draft, collectedAt));
    }

    private SaveOutcome saveOrMergeOnce(Source source, ArticleDraft draft, Instant collectedAt) {
        if (draft.cveId() != null) {
            // 행 잠금 조회: 두 출처가 같은 행을 동시에 병합해도 한쪽 변경이 덮이지 않음
            Optional<Article> sameCveArticle = articleRepository.findByCveIdForUpdate(draft.cveId());
            if (sameCveArticle.isPresent()) {
                return mergeIntoSameCve(sameCveArticle.get(), source, draft);
            }
        }
        // 같은 링크 재수집: 요약 입력·이미지 주소가 없던 기사만 이번 값으로 채움
        Optional<Article> sameLinkArticle = articleRepository.findByOriginalUrl(draft.originalUrl());
        if (sameLinkArticle.isPresent()) {
            return sameLinkArticle.get().mergeSourceDetails(draft) ? SaveOutcome.MERGED : SaveOutcome.DUPLICATE;
        }
        articleRepository.save(Article.create(source, draft, collectedAt));
        return SaveOutcome.CREATED;
    }

    // 병합 규칙 4개: NVD 가 KEV·GHSA 행을 만나면 링크 교체(사실 값은 NVD 라벨만 교체), 그 밖은 새 라벨의 사실 값만 추가,
    // KEV 면 등재 표시, 긴급은 기존 || 새 값
    private SaveOutcome mergeIntoSameCve(Article existing, Source source, ArticleDraft draft) {
        boolean isChanged = false;
        if (isNvdSource(source) && !isNvdSource(existing.getSource())) {
            existing.replaceLinkWithNvd(source, draft);
            isChanged = true;
        } else if (existing.mergeSourceDetails(draft)) {
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
