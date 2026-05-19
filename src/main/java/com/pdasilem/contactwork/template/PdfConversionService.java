package com.pdasilem.contactwork.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pdasilem.contactwork.onlyoffice.OnlyOfficeAccessTokenService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PdfConversionService {
    private static final Logger log = LoggerFactory.getLogger(PdfConversionService.class);

    private final String documentBaseUrl;
    private final OnlyOfficeAccessTokenService accessTokenService;
    private final ConversionFileStore conversionFileStore;
    private final RestClient restClient;

    public PdfConversionService(
            ConversionFileStore conversionFileStore,
            OnlyOfficeAccessTokenService accessTokenService,
            @Value("${app.onlyoffice.converter-base-url:http://onlyoffice}") String converterBaseUrl,
            @Value("${app.onlyoffice.document-base-url:http://app:8083}") String documentBaseUrl
    ) {
        this.documentBaseUrl = trimTrailingSlash(documentBaseUrl);
        this.accessTokenService = accessTokenService;
        this.conversionFileStore = conversionFileStore;
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(converterBaseUrl))
                .build();
    }

    public Path convertToPdf(Path docxPath) {
        UUID fileId = conversionFileStore.register(
                docxPath,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );
        try {
            Path pdfPath = docxPath.getParent().resolve(replaceExtension(docxPath.getFileName().toString(), ".pdf"));
            byte[] pdfBytes = requestPdfBytes(docxPath, fileId);
            Files.write(pdfPath, pdfBytes);
            if (!Files.exists(pdfPath) || Files.size(pdfPath) == 0) {
                throw new IllegalStateException("ONLYOFFICE returned an empty PDF");
            }
            log.debug("Converted {} to {}", docxPath, pdfPath);
            return pdfPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to convert DOCX to PDF", ex);
        } finally {
            conversionFileStore.remove(fileId);
        }
    }

    private byte[] requestPdfBytes(Path docxPath, UUID fileId) {
        String documentUrl = buildDocumentUrl(fileId);
        ConversionRequest request = new ConversionRequest(
                false,
                "docx",
                docxPath.getFileName().toString(),
                "pdf",
                buildDocumentKey(docxPath),
                documentUrl
        );

        ConversionResponse response = restClient.post()
                .uri("/converter")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ConversionResponse.class);

        if (response == null) {
            throw new IllegalStateException("ONLYOFFICE conversion returned no response");
        }
        if (response.error() != null && response.error() != 0) {
            throw new IllegalStateException("ONLYOFFICE conversion failed with error code " + response.error());
        }
        if (response.endConvert() == null || !response.endConvert()) {
            throw new IllegalStateException("ONLYOFFICE conversion did not complete synchronously");
        }
        if (response.fileUrl() == null || response.fileUrl().isBlank()) {
            throw new IllegalStateException("ONLYOFFICE conversion returned no file URL");
        }

        byte[] pdfBytes = restClient.get()
                .uri(response.fileUrl())
                .accept(MediaType.APPLICATION_PDF)
                .retrieve()
                .body(byte[].class);

        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("ONLYOFFICE returned no PDF bytes");
        }
        return pdfBytes;
    }

    String buildDocumentUrl(UUID fileId) {
        return UriComponentsBuilder.fromUriString(documentBaseUrl + "/internal/conversion/files/" + fileId)
                .queryParam("token", accessTokenService.conversionToken(fileId))
                .build()
                .toUriString();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildDocumentKey(Path docxPath) {
        try {
            return docxPath.getFileName() + "-" + Files.getLastModifiedTime(docxPath).toMillis() + "-" + Files.size(docxPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build ONLYOFFICE document key", ex);
        }
    }

    private String replaceExtension(String filename, String newExtension) {
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return filename + newExtension;
        }
        return filename.substring(0, index) + newExtension;
    }

    private record ConversionRequest(
            boolean async,
            String filetype,
            String title,
            String outputtype,
            String key,
            String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConversionResponse(
            Integer error,
            Boolean endConvert,
            String fileUrl,
            Integer percent
    ) {
    }
}
