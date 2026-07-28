package com.ecgcare.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
    }

    @Test
    void generateDekProduces256BitAesKey() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        assertThat(dek.getAlgorithm()).isEqualTo("AES");
        assertThat(dek.getEncoded()).hasSize(32);
    }

    @Test
    void encryptDecryptRoundTripRestoresPlaintext() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        byte[] plaintext = "sensitive patient record".getBytes(StandardCharsets.UTF_8);

        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(plaintext, dek);
        byte[] decrypted = encryptionService.decrypt(encrypted, dek);

        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(encrypted.iv()).hasSize(12);
        assertThat(encrypted.tag()).hasSize(16);
        assertThat(encrypted.data()).isNotEqualTo(plaintext);
    }

    @Test
    void decryptWithWrongKeyFails() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        SecretKey wrongKey = encryptionService.generateDEK();
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(
                "secret".getBytes(StandardCharsets.UTF_8), dek);

        assertThatThrownBy(() -> encryptionService.decrypt(encrypted, wrongKey))
                .isInstanceOf(Exception.class);
    }

    @Test
    void decryptTamperedCiphertextFails() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(
                "integrity matters".getBytes(StandardCharsets.UTF_8), dek);

        byte[] tamperedData = encrypted.data().clone();
        tamperedData[0] ^= 0x01;
        EncryptionService.EncryptedData tampered = new EncryptionService.EncryptedData(
                tamperedData, encrypted.iv(), encrypted.tag());

        assertThatThrownBy(() -> encryptionService.decrypt(tampered, dek))
                .isInstanceOf(Exception.class);
    }

    @Test
    void encryptProducesDifferentCiphertextEachTime() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        byte[] plaintext = "same input".getBytes(StandardCharsets.UTF_8);

        EncryptionService.EncryptedData first = encryptionService.encrypt(plaintext, dek);
        EncryptionService.EncryptedData second = encryptionService.encrypt(plaintext, dek);

        assertThat(first.iv()).isNotEqualTo(second.iv());
        assertThat(first.data()).isNotEqualTo(second.data());
    }

    @Test
    void encryptJsonDecryptJsonRoundTrip() throws Exception {
        Map<String, Object> data = Map.of("name", "Baby A", "age", 1, "diagnosis", "ASD");

        EncryptionService.EncryptedDataWithKey encrypted = encryptionService.encryptJson(data);
        Map<String, Object> decrypted = encryptionService.decryptJson(
                encrypted.encryptedData(), encrypted.key());

        assertThat(decrypted).containsEntry("name", "Baby A")
                .containsEntry("age", 1)
                .containsEntry("diagnosis", "ASD");
    }

    @Test
    void packUnpackRoundTripPreservesAllParts() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(
                "pack me".getBytes(StandardCharsets.UTF_8), dek);

        byte[] packed = encryptionService.packEncrypted(encrypted);
        EncryptionService.EncryptedData unpacked = encryptionService.unpackEncrypted(packed);

        assertThat(unpacked.iv()).isEqualTo(encrypted.iv());
        assertThat(unpacked.tag()).isEqualTo(encrypted.tag());
        assertThat(unpacked.data()).isEqualTo(encrypted.data());
        assertThat(encryptionService.decrypt(unpacked, dek))
                .isEqualTo("pack me".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void wrapUnwrapKeyWithRsaRoundTrip() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        SecretKey dek = encryptionService.generateDEK();

        EncryptionService.EncryptedData wrapped = encryptionService.wrapKey(dek, keyPair.getPublic());
        SecretKey unwrapped = encryptionService.unwrapKey(wrapped, keyPair.getPrivate());

        assertThat(unwrapped.getEncoded()).isEqualTo(dek.getEncoded());
        assertThat(wrapped.iv()).isEmpty();
        assertThat(wrapped.tag()).isEmpty();
    }

    @Test
    void unwrapWithWrongPrivateKeyFails() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair rightPair = keyGen.generateKeyPair();
        KeyPair wrongPair = keyGen.generateKeyPair();
        SecretKey dek = encryptionService.generateDEK();

        EncryptionService.EncryptedData wrapped = encryptionService.wrapKey(dek, rightPair.getPublic());

        assertThatThrownBy(() -> encryptionService.unwrapKey(wrapped, wrongPair.getPrivate()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deriveKekIsDeterministicForSamePasswordAndSalt() throws Exception {
        byte[] salt = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

        SecretKey first = encryptionService.deriveKEK("password123".toCharArray(), salt, 1000);
        SecretKey second = encryptionService.deriveKEK("password123".toCharArray(), salt, 1000);
        SecretKey differentSalt = encryptionService.deriveKEK("password123".toCharArray(),
                "fedcba9876543210".getBytes(StandardCharsets.UTF_8), 1000);
        SecretKey differentPassword = encryptionService.deriveKEK("password456".toCharArray(), salt, 1000);

        assertThat(first.getEncoded()).isEqualTo(second.getEncoded());
        assertThat(first.getEncoded()).isNotEqualTo(differentSalt.getEncoded());
        assertThat(first.getEncoded()).isNotEqualTo(differentPassword.getEncoded());
    }

    @Test
    void serializeDeserializeKeyRoundTrip() throws Exception {
        SecretKey dek = encryptionService.generateDEK();
        byte[] serialized = encryptionService.serializeKey(dek);
        SecretKey restored = encryptionService.deserializeKey(serialized);

        assertThat(restored.getEncoded()).isEqualTo(dek.getEncoded());
        assertThat(restored.getAlgorithm()).isEqualTo("AES");
    }

    @Test
    void publicAndPrivateKeyFromBytesRoundTrip() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        PublicKey publicKey = encryptionService.publicKeyFromBytes(keyPair.getPublic().getEncoded());
        PrivateKey privateKey = encryptionService.privateKeyFromBytes(keyPair.getPrivate().getEncoded());

        assertThat(publicKey.getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        assertThat(privateKey.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());

        // Prove the reconstructed keys actually work together
        SecretKey dek = encryptionService.generateDEK();
        EncryptionService.EncryptedData wrapped = encryptionService.wrapKey(dek, publicKey);
        assertThat(encryptionService.unwrapKey(wrapped, privateKey).getEncoded())
                .isEqualTo(dek.getEncoded());
    }
}
