package io.coreplatform.ai.infrastructure.security;

import io.coreplatform.ai.infrastructure.config.CoreAiProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private static final String MASTER_KEY = "Y29yZS1haS1sb2NhbC1kZXYtbWFzdGVyLWtleS0wMDE=";

    @Test
    void shouldEncryptDecryptAndMaskSecretWithoutExposingPlainText() {
        AesGcmSecretCipher cipher = cipher(MASTER_KEY);

        String encrypted = cipher.encrypt("sk-test-12345678");

        assertThat(encrypted)
                .startsWith("v1:")
                .doesNotContain("sk-test-12345678");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("sk-test-12345678");
        assertThat(cipher.mask("sk-test-12345678")).isEqualTo("sk-****5678");
        assertThat(cipher.encrypt("sk-test-12345678")).isNotEqualTo(encrypted);
    }

    @Test
    void shouldRejectInvalidMasterKeyLength() {
        assertThatThrownBy(() -> cipher("c2hvcnQ="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private AesGcmSecretCipher cipher(String masterKey) {
        return new AesGcmSecretCipher(new CoreAiProperties(
                new CoreAiProperties.Security("local"),
                new CoreAiProperties.Crypto(masterKey),
                new CoreAiProperties.Provider(2_097_152, false, 300_000)
        ));
    }
}
