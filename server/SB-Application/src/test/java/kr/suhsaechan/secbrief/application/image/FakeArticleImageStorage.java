package kr.suhsaechan.secbrief.application.image;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.image.ArticleImageStorage;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 테스트용 저장소: 메모리에 보관, 준비 여부·저장 장애를 켜고 끔
 */
@Getter
@Setter
class FakeArticleImageStorage implements ArticleImageStorage {

    private final Map<String, byte[]> storedBytesByObjectKey = new LinkedHashMap<>();
    private boolean isAvailable = true;
    private boolean isStoreFailing;

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public void store(String objectKey, byte[] jpegBytes) {
        if (isStoreFailing) {
            throw new CustomException(ErrorCode.IMAGE_STORAGE_ERROR, "store failed : AccessDenied");
        }
        storedBytesByObjectKey.put(objectKey, jpegBytes);
    }

    @Override
    public Optional<byte[]> read(String objectKey) {
        return Optional.ofNullable(storedBytesByObjectKey.get(objectKey));
    }
}
