package kr.suhsaechan.secbrief.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 설정 비밀값 암호화: AES-256-GCM, 저장 형식 enc:v1:{base64(nonce 12바이트 ‖ 암호문 ‖ tag 16바이트)}, AAD = 설정 키 이름
 * 마스터 키(secbrief.secret.master-key)가 없어도 서버는 뜸: 암호화는 거부, 복호화는 null (평문·키는 로그·예외에 넣지 않음)
 */
@Slf4j
@Component
public class SecretCipher {

    // 암호문 접두사 (형식 판별 + 이후 형식 변경 대비 버전)
    public static final String ENCRYPTED_PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DIGEST_ALGORITHM = "SHA-256";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    // 마스터 키 문자열 > SHA-256 > 32바이트 키 (미설정이면 null)
    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${secbrief.secret.master-key:}") String masterKeyText) {
        this.masterKey = toMasterKey(masterKeyText);
        if (masterKey == null) {
            log.warn("[secret cipher] master key not configured : secret settings cannot be saved or read");
        }
    }

    /** 마스터 키 설정 여부 (false 면 비밀 설정 저장 거부, 읽기는 없음으로 처리) */
    public boolean isAvailable() {
        return masterKey != null;
    }

    /**
     * 평문 > enc:v1:... (호출마다 새 nonce). 마스터 키가 없으면 SECRET_MASTER_KEY_MISSING (평문으로 저장하지 않음)
     */
    public String encrypt(String configKeyName, String plainValue) {
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.SECRET_MASTER_KEY_MISSING);
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(configKeyName.getBytes(StandardCharsets.UTF_8));
            byte[] cipherTextAndTag = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[NONCE_BYTES + cipherTextAndTag.length];
            System.arraycopy(nonce, 0, payload, 0, NONCE_BYTES);
            System.arraycopy(cipherTextAndTag, 0, payload, NONCE_BYTES, cipherTextAndTag.length);
            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            log.error("[secret cipher] encrypt failed 500 : key={}, cause={}", configKeyName,
                    exception.getClass().getSimpleName());
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "secret encrypt failed");
        }
    }

    /**
     * enc:v1:... > 평문. 마스터 키 없음·형식 오류·변조·다른 설정 키의 값(AAD 불일치)은 null + 경고 로그 (키 이름만)
     */
    public String decrypt(String configKeyName, String storedValue) {
        if (!isAvailable()) {
            return null;
        }
        if (!isEncrypted(storedValue)) {
            log.warn("[secret cipher] decrypt failed : not an encrypted value : key={}", configKeyName);
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("payload too short");
            }
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] cipherTextAndTag = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(configKeyName.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(cipherTextAndTag), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            log.warn("[secret cipher] decrypt failed : key={}, cause={}", configKeyName,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    public static boolean isEncrypted(String storedValue) {
        return storedValue != null && storedValue.startsWith(ENCRYPTED_PREFIX);
    }

    private static SecretKeySpec toMasterKey(String masterKeyText) {
        if (masterKeyText == null || masterKeyText.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance(KEY_DIGEST_ALGORITHM)
                    .digest(masterKeyText.strip().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
        } catch (GeneralSecurityException exception) {
            // SHA-256 은 모든 JVM 에 있음 (여기 오면 실행 환경 문제)
            throw new IllegalStateException("master key digest unavailable", exception);
        }
    }
}
