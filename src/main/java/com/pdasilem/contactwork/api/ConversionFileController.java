package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.template.ConversionFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/conversion/files")
public class ConversionFileController {

    private final ConversionFileStore conversionFileStore;

    public ConversionFileController(ConversionFileStore conversionFileStore) {
        this.conversionFileStore = conversionFileStore;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> getFile(@PathVariable UUID fileId) throws IOException {
        ConversionFileStore.StoredFile storedFile = conversionFileStore.get(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Conversion file not found: " + fileId));
        byte[] bytes = Files.readAllBytes(storedFile.path());
        MediaType mediaType = MediaType.parseMediaType(storedFile.mediaType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(bytes);
    }
}
