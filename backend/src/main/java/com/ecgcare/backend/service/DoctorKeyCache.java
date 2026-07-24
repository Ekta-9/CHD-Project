package com.ecgcare.backend.service;

import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Holds each doctor's decrypted RSA private key in memory for the lifetime of
// their login session, so it never has to be persisted or re-sent after login.
// Lost on logout or server restart - the doctor just logs in again to repopulate it.
@Service
public class DoctorKeyCache {
    private final ConcurrentHashMap<UUID, PrivateKey> cache = new ConcurrentHashMap<>();

    public void put(UUID sessionId, PrivateKey privateKey) {
        cache.put(sessionId, privateKey);
    }

    public Optional<PrivateKey> get(UUID sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(sessionId));
    }

    public void evict(UUID sessionId) {
        cache.remove(sessionId);
    }
}
