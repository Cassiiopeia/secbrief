package kr.suhsaechan.secbrief.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 비밀값 암호화: 왕복, 저장 형식(enc:v1: + nonce 12 ‖ 암호문 ‖ tag 16), 다른 설정 키(AAD)·다른 마스터 키·변조는 복호화 실패,
 * 마스터 키가 없으면 암호화 거부·복호화 null
 */
class SecretCipherTest {

    private static final String CONFIG_KEY_NAME = "ANTHROPIC_API_KEYS";
    private static final String PLAIN_VALUE = "[{\"id\":\"a1b2c3d4\",\"nickname\":\"운영\",\"apiKey\":\"sk-test-000\",\"isEnabled\":true}]";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;

    private final SecretCipher cipher = new SecretCipher("test-master-key");

    @Test
    void decryptsWhatItEncrypted() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);

        assertThat(cipher.isAvailable()).isTrue();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, storedValue)).isEqualTo(PLAIN_VALUE);
    }

    @Test
    void storesPrefixedBase64OfNonceCipherTextAndTagWithoutPlainText() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);

        assertThat(storedValue).startsWith("enc:v1:").doesNotContain("sk-test").doesNotContain("운영");
        assertThat(SecretCipher.isEncrypted(storedValue)).isTrue();
        byte[] payload = Base64.getDecoder().decode(storedValue.substring("enc:v1:".length()));
        assertThat(payload).hasSize(NONCE_BYTES + PLAIN_VALUE.getBytes(StandardCharsets.UTF_8).length + TAG_BYTES);
    }

    @Test
    void usesNewNonceForEveryEncryption() {
        assertThat(cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE)).isNotEqualTo(cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE));
    }

    @Test
    void failsToDecryptValueMovedToAnotherConfigKey() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);

        assertThat(cipher.decrypt("ANOTHER_SECRET_KEY", storedValue)).isNull();
    }

    @Test
    void failsToDecryptWithAnotherMasterKey() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);

        assertThat(new SecretCipher("another-master-key").decrypt(CONFIG_KEY_NAME, storedValue)).isNull();
        // 같은 문자열이면 다른 인스턴스(다시 띄운 서버)에서도 풀림
        assertThat(new SecretCipher(" test-master-key ").decrypt(CONFIG_KEY_NAME, storedValue)).isEqualTo(PLAIN_VALUE);
    }

    @Test
    void detectsTamperedCipherTextTagAndNonce() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);
        byte[] payload = Base64.getDecoder().decode(storedValue.substring("enc:v1:".length()));

        assertThat(cipher.decrypt(CONFIG_KEY_NAME, toStoredValueWithFlippedBit(payload, 0))).isNull();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, toStoredValueWithFlippedBit(payload, NONCE_BYTES + 3))).isNull();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, toStoredValueWithFlippedBit(payload, payload.length - 1))).isNull();
    }

    @Test
    void returnsNullForMalformedStoredValues() {
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, "plain text value")).isNull();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, "enc:v1:not base64 !!")).isNull();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, "enc:v1:" + Base64.getEncoder().encodeToString(new byte[5]))).isNull();
        assertThat(cipher.decrypt(CONFIG_KEY_NAME, null)).isNull();
        assertThat(SecretCipher.isEncrypted("plain text value")).isFalse();
    }

    @Test
    void withoutMasterKeyRefusesToEncryptAndReadsNothing() {
        String storedValue = cipher.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE);

        for (String missingMasterKey : new String[] {null, "", "   "}) {
            SecretCipher cipherWithoutMasterKey = new SecretCipher(missingMasterKey);
            assertThat(cipherWithoutMasterKey.isAvailable()).isFalse();
            assertThatThrownBy(() -> cipherWithoutMasterKey.encrypt(CONFIG_KEY_NAME, PLAIN_VALUE))
                    .isInstanceOf(CustomException.class)
                    .hasMessageNotContaining("sk-test")
                    .extracting("errorCode").isEqualTo(ErrorCode.SECRET_MASTER_KEY_MISSING);
            assertThat(cipherWithoutMasterKey.decrypt(CONFIG_KEY_NAME, storedValue)).isNull();
        }
    }

    private static String toStoredValueWithFlippedBit(byte[] payload, int byteIndex) {
        byte[] tamperedPayload = payload.clone();
        tamperedPayload[byteIndex] ^= 0x01;
        return "enc:v1:" + Base64.getEncoder().encodeToString(tamperedPayload);
    }
}
