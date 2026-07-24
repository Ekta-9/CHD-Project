package com.ecgcare.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
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

    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String KEK_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int KEK_ITERATIONS = 210_000;
    private static final int KEK_KEY_LENGTH = 256;

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

    // Wraps a DEK with the doctor's real RSA public key (RSA-OAEP). Only the
    // matching private key can unwrap it back.
    public EncryptedData wrapKey(SecretKey dek, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] wrapped = cipher.doFinal(serializeKey(dek));
        // RSA-OAEP is not a streaming/AEAD mode - there's no IV or tag to carry,
        // but EncryptedData's shape is shared with the AES-GCM paths, so these
        // are just empty rather than unused.
        return new EncryptedData(wrapped, new byte[0], new byte[0]);
    }

    public SecretKey unwrapKey(EncryptedData wrappedKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] keyBytes = cipher.doFinal(wrappedKey.data());
        return deserializeKey(keyBytes);
    }

    // Derives a Key-Encryption-Key from a doctor's password. Used to encrypt
    // their RSA private key at rest - never persisted itself.
    public SecretKey deriveKEK(char[] password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEK_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEK_KEY_LENGTH);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } finally {
            spec.clearPassword();
        }
    }

    public PublicKey publicKeyFromBytes(byte[] bytes) throws Exception {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    public PrivateKey privateKeyFromBytes(byte[] bytes) throws Exception {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    // Packs an AES-GCM EncryptedData into a single blob (iv || tag || ciphertext)
    // so it fits in a single DB column, using the fixed GCM_IV_LENGTH/GCM_TAG_LENGTH.
    public byte[] packEncrypted(EncryptedData encryptedData) {
        ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + GCM_TAG_LENGTH + encryptedData.data().length);
        buffer.put(encryptedData.iv());
        buffer.put(encryptedData.tag());
        buffer.put(encryptedData.data());
        return buffer.array();
    }

    public EncryptedData unpackEncrypted(byte[] packed) {
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] tag = new byte[GCM_TAG_LENGTH];
        byte[] data = new byte[packed.length - GCM_IV_LENGTH - GCM_TAG_LENGTH];
        System.arraycopy(packed, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(packed, GCM_IV_LENGTH, tag, 0, GCM_TAG_LENGTH);
        System.arraycopy(packed, GCM_IV_LENGTH + GCM_TAG_LENGTH, data, 0, data.length);
        return new EncryptedData(data, iv, tag);
    }
}
