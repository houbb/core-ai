package io.coreplatform.ai.application.port;

public interface SecretCipherPort {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String mask(String plainText);
}
