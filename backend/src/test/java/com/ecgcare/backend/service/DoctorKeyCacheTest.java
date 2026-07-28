package com.ecgcare.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorKeyCacheTest {

    private DoctorKeyCache cache;
    private PrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        cache = new DoctorKeyCache();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        privateKey = keyGen.generateKeyPair().getPrivate();
    }

    @Test
    void putThenGetReturnsKey() {
        UUID sessionId = UUID.randomUUID();
        cache.put(sessionId, privateKey);

        assertThat(cache.get(sessionId)).contains(privateKey);
    }

    @Test
    void getUnknownSessionReturnsEmpty() {
        assertThat(cache.get(UUID.randomUUID())).isEmpty();
    }

    @Test
    void getNullSessionReturnsEmpty() {
        assertThat(cache.get(null)).isEmpty();
    }

    @Test
    void evictRemovesKey() {
        UUID sessionId = UUID.randomUUID();
        cache.put(sessionId, privateKey);
        cache.evict(sessionId);

        assertThat(cache.get(sessionId)).isEmpty();
    }

    @Test
    void evictUnknownSessionDoesNotThrow() {
        cache.evict(UUID.randomUUID());
    }
}
