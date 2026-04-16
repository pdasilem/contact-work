package com.pdasilem.contactwork.template;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class ConversionFileStore {
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ConcurrentMap<UUID, StoredFile> files = new ConcurrentHashMap<>();

    public UUID register(Path path, String mediaType) {
        cleanupExpired();
        UUID id = UUID.randomUUID();
        files.put(id, new StoredFile(path, mediaType, Instant.now().plus(TTL)));
        return id;
    }

    public Optional<StoredFile> get(UUID id) {
        StoredFile storedFile = files.get(id);
        if (storedFile == null) {
            return Optional.empty();
        }
        if (storedFile.expiresAt().isBefore(Instant.now())) {
            files.remove(id);
            return Optional.empty();
        }
        return Optional.of(storedFile);
    }

    public void remove(UUID id) {
        files.remove(id);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        files.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record StoredFile(Path path, String mediaType, Instant expiresAt) {
    }
}
