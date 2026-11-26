package kr.suhsaechan.secbrief.advisory.service;

import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSharePreview;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 미리보기 기사 조회: 기사 1건 > 미리보기 값 (없는 id 는 빈 값, 페이지가 404 화면으로 처리)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArticleSharePreviewQueryService {

    private final ArticleRepository articleRepository;

    public Optional<ArticleSharePreview> findSharePreview(Long articleId) {
        return articleRepository.findById(articleId).map(ArticleSharePreview::from);
    }
}
