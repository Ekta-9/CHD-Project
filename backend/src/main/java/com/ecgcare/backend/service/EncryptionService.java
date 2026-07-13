package com.ecgcare.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class EncryptionService {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_SIZE = 256;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecretKey generateDEK() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        return keyGenerator.generateKey();
    }

    public EncryptedData encrypt(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] cipherText = cipher.doFinal(data);

        // Extract tag (last 16 bytes) and ciphertext
        byte[] tag = new byte[GCM_TAG_LENGTH];
        byte[] encryptedData = new byte[cipherText.length - GCM_TAG_LENGTH];
        System.arraycopy(cipherText, 0, encryptedData, 0, encryptedData.length);
        System.arraycopy(cipherText, encryptedData.length, tag, 0, GCM_TAG_LENGTH);

        return new EncryptedData(encryptedData, iv, tag);
    }

    public byte[] decrypt(EncryptedData encryptedData, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.iv());
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        // Combine encrypted data and tag
        byte[] cipherText = new byte[encryptedData.data().length + encryptedData.tag().length];
        System.arraycopy(encryptedData.data(), 0, cipherText, 0, encryptedData.data().length);
        System.arraycopy(encryptedData.tag(), 0, cipherText, encryptedData.data().length, encryptedData.tag().length);

        return cipher.doFinal(cipherText);
    }

    public EncryptedDataWithKey encryptJson(Map<String, Object> data) throws Exception {
        byte[] jsonBytes = objectMapper.writeValueAsBytes(data);
        SecretKey dek = generateDEK();
        EncryptedData encrypted = encrypt(jsonBytes, dek);
        return new EncryptedDataWithKey(encrypted, dek);
    }

    public Map<String, Object> decryptJson(EncryptedData encryptedData, SecretKey key) throws Exception {
        byte[] decryptedBytes = decrypt(encryptedData, key);
        return objectMapper.readValue(decryptedBytes, Map.class);
    }

    public record EncryptedData(byte[] data, byte[] iv, byte[] tag) {
    }

    public record EncryptedDataWithKey(EncryptedData encryptedData, SecretKey key) {
        public EncryptedData encryptedData() {
            return encryptedData;
        }

        public SecretKey key() {
            return key;
        }
    }

    public byte[] serializeKey(SecretKey key) {
        return key.getEncoded();
    }

    public SecretKey deserializeKey(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    // Simplified key wrapping (for production, use RSA-OAEP)
    public EncryptedData wrapKey(SecretKey dek, byte[] publicKey) throws Exception {
        // Simplified: derive a symmetric key from public key hash for testing
        // In production, use RSA-OAEP with the public key
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyHash = digest.digest(publicKey);
        // Use first 32 bytes as AES key
        byte[] derivedKey = new byte[32];
        System.arraycopy(keyHash, 0, derivedKey, 0, Math.min(32, keyHash.length));
        SecretKeySpec wrappingKey = new SecretKeySpec(derivedKey, ALGORITHM);
        return encrypt(serializeKey(dek), wrappingKey);
    }

    public SecretKey unwrapKey(EncryptedData wrappedKey, byte[] publicKey) throws Exception {
        // Simplified: derive the same symmetric key from public key hash
        // In production, use RSA-OAEP with the private key
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] keyHash = digest.digest(publicKey);
        byte[] derivedKey = new byte[32];
        System.arraycopy(keyHash, 0, derivedKey, 0, Math.min(32, keyHash.length));
        SecretKeySpec wrappingKey = new SecretKeySpec(derivedKey, ALGORITHM);
        byte[] keyBytes = decrypt(wrappedKey, wrappingKey);
        return deserializeKey(keyBytes);
    }
}
