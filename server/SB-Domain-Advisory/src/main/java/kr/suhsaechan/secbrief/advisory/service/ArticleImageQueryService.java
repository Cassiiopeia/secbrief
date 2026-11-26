package kr.suhsaechan.secbrief.advisory.service;

import kr.suhsaechan.secbrief.advisory.image.ArticleImageStorage;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 기사 대표 이미지 읽기: 저장된 객체 키 조회 > 저장소에서 JPEG 바이트 (저장소 호출 동안에는 트랜잭션을 잡지 않음)
 * 없음 3가지(기사 없음, 저장 전(STORED 아님), 객체 없음)는 RESOURCE_NOT_FOUND, 저장소 장애는 IMAGE_STORAGE_ERROR
 */
@Service
@RequiredArgsConstructor
public class ArticleImageQueryService {

    private final ArticleRepository articleRepository;
    private final ArticleImageStorage articleImageStorage;

    public byte[] findStoredImage(Long articleId) {
        String imageObjectKey = articleRepository.findStoredImageObjectKey(articleId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!articleImageStorage.isAvailable()) {
            throw new CustomException(ErrorCode.IMAGE_STORAGE_ERROR, "storage not configured");
        }
        return articleImageStorage.read(imageObjectKey)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
