package kr.suhsaechan.secbrief.application.image;

import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미지 결과 기록: 저장 성공(객체 키 > STORED) / 실패(시도 횟수 +1, 3회면 FAILED), 건마다 짧은 트랜잭션
 * 내려받기·저장소 호출 동안에는 트랜잭션을 잡지 않음
 */
@Component
@RequiredArgsConstructor
public class ArticleImageResultRecorder {

    private final ArticleRepository articleRepository;

    @Transactional
    public void recordStored(Long articleId, String imageObjectKey) {
        articleRepository.findById(articleId).orElseThrow().getImageState().markStored(imageObjectKey);
    }

    @Transactional
    public void recordFailure(Long articleId) {
        articleRepository.findById(articleId).orElseThrow().getImageState().recordFailure();
    }
}
