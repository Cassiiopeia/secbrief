package kr.suhsaechan.secbrief.application.collect;

import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchMethod;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchTarget;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KISA 게시판 새 글의 요약 입력: RSS 에 요약문이 없어 게시글 본문 칸(표는 행 단위)을 받아 요약 입력으로 (#57)
 * 받은 글은 요약 대기(PENDING) 동안만 보관, 요약이 끝나면 지움. 요청 사이 2초 (SourceTextRefetcher), 못 받으면 입력 없이 저장
 */
@Slf4j
@Component
public class KisaPageSourceTextFiller {

    private final SourceTextRefetcher sourceTextRefetcher;
    // secbrief.collect.kisa-page-text-enabled: 끄면 KISA 글은 요약 입력 없이 저장 (원문 링크로 열림)
    private final boolean isEnabled;

    public KisaPageSourceTextFiller(SourceTextRefetcher sourceTextRefetcher,
                                    @Value("${secbrief.collect.kisa-page-text-enabled}") boolean isEnabled) {
        this.sourceTextRefetcher = sourceTextRefetcher;
        this.isEnabled = isEnabled;
    }

    /**
     * 초안 1건: 대상인지 확인(켜짐, KISA 게시판 출처, 요약 입력 없음) > 게시글 본문 받기 > 요약할 내용이 있으면 요약 입력으로, 아니면 그대로
     */
    public ArticleDraft fillSourceText(Source source, ArticleDraft draft) {
        if (!shouldFill(source, draft)) {
            return draft;
        }
        SourceTextRefetchResult refetchResult;
        try {
            refetchResult = sourceTextRefetcher.refetch(
                    new SourceTextRefetchTarget(source.getCode(), draft.title(), draft.originalUrl(), draft.cveId()));
        } catch (RuntimeException exception) {
            // 받기 실패는 그 글만 입력 없이 저장 (수집은 계속)
            log.warn("[kisa page text] failed : source={}, cause={}", source.getCode(), exception.getClass().getSimpleName());
            return draft;
        }
        if (!refetchResult.isRecovered()) {
            log.info("[kisa page text] not filled : source={}, status={}, cause={}",
                    source.getCode(), refetchResult.status(), refetchResult.failureReason());
            return draft;
        }
        log.info("[kisa page text] filled : source={}, length={}", source.getCode(), refetchResult.sourceText().length());
        return draft.withSourceText(refetchResult.sourceText());
    }

    private boolean shouldFill(Source source, ArticleDraft draft) {
        return isEnabled && draft.sourceText() == null
                && SourceTextRefetchMethod.of(source.getCode()) == SourceTextRefetchMethod.KISA_BOARD_VIEW_PAGE;
    }
}
