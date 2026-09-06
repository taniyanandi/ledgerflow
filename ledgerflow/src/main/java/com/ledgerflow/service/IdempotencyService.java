package com.ledgerflow.service;

import com.ledgerflow.domain.IdempotencyRecord;
import com.ledgerflow.repository.IdempotencyRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRepository repository;

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    public Optional<IdempotencyRecord> find(String key) {
        return repository.findByIdempotencyKey(key);
    }

    public void save(IdempotencyRecord record) {
        repository.save(record);
    }

    /** SHA-256 of the request body, so a key reused with a different payload is detectable. */
    public String hash(String requestBody) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
