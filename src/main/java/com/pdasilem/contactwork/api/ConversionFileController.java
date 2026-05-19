package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.onlyoffice.OnlyOfficeAccessTokenService;
import com.pdasilem.contactwork.template.ConversionFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/conversion/files")
public class ConversionFileController {

    private final ConversionFileStore conversionFileStore;
    private final OnlyOfficeAccessTokenService accessTokenService;

    public ConversionFileController(
            ConversionFileStore conversionFileStore,
            OnlyOfficeAccessTokenService accessTokenService
    ) {
        this.conversionFileStore = conversionFileStore;
        this.accessTokenService = accessTokenService;
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> getFile(
            @PathVariable UUID fileId,
            @RequestParam(required = false) String token
    ) throws IOException {
        if (!accessTokenService.isValidConversionToken(fileId, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ConversionFileStore.StoredFile storedFile = conversionFileStore.get(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Conversion file not found: " + fileId));
        byte[] bytes = Files.readAllBytes(storedFile.path());
        MediaType mediaType = MediaType.parseMediaType(storedFile.mediaType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(bytes);
    }
}
