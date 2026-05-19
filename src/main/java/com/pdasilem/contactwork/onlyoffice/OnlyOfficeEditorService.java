package com.pdasilem.contactwork.onlyoffice;

import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class OnlyOfficeEditorService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeEditorService.class);
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ProjectAssetService projectAssetService;
    private final RestClient restClient;
    private final OnlyOfficeAccessTokenService accessTokenService;
    private final String publicBaseUrl;
    private final String documentBaseUrl;
    private final String internalDownloadBaseUrl;

    public OnlyOfficeEditorService(
            ProjectAssetService projectAssetService,
            OnlyOfficeAccessTokenService accessTokenService,
            @Value("${app.onlyoffice.public-base-url:http://127.0.0.1:8084}") String publicBaseUrl,
            @Value("${app.onlyoffice.document-base-url:http://app:8083}") String documentBaseUrl,
            @Value("${app.onlyoffice.internal-download-base-url:http://onlyoffice}") String internalDownloadBaseUrl
    ) {
        this.projectAssetService = projectAssetService;
        this.restClient = RestClient.builder().build();
        this.accessTokenService = accessTokenService;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.documentBaseUrl = trimTrailingSlash(documentBaseUrl);
        this.internalDownloadBaseUrl = trimTrailingSlash(internalDownloadBaseUrl);
    }

    public String apiScriptUrl() {
        return publicBaseUrl + "/web-apps/apps/api/documents/api.js";
    }

    public Map<String, Object> editorConfig(UUID projectId) {
        ProjectAsset asset = projectAssetService.activeLetterOrThrow(projectId);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", "word");
        config.put("document", document(projectId, asset));
        config.put("editorConfig", editor(projectId));
        return config;
    }

    public ProjectAsset activeLetter(UUID projectId) {
        return projectAssetService.activeLetterOrThrow(projectId);
    }

    public byte[] activeLetterBytes(UUID projectId) {
        ProjectAsset asset = activeLetter(projectId);
        try {
            return Files.readAllBytes(Path.of(asset.getStoredPath()));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read active letter template", ex);
        }
    }

    public void handleCallback(UUID projectId, OnlyOfficeCallbackRequest request) {
        if (request == null || request.status() == null) {
            return;
        }
        if ((request.status() == 2 || request.status() == 6) && request.url() != null && !request.url().isBlank()) {
            URI downloadUri = rewriteDownloadUri(request.url());
            try {
                byte[] updated = restClient.get()
                        .uri(downloadUri)
                        .retrieve()
                        .body(byte[].class);
                projectAssetService.overwriteActiveLetter(projectId, updated);
            } catch (Exception ex) {
                log.warn("ONLYOFFICE callback save failed: projectId={}, status={}, callbackUrl={}, downloadUrl={}",
                        projectId, request.status(), request.url(), downloadUri, ex);
                throw ex;
            }
        }
    }

    URI rewriteDownloadUri(String callbackUrl) {
        URI uri = URI.create(callbackUrl);
        URI publicUri = URI.create(publicBaseUrl);
        if (sameOrigin(uri, publicUri)) {
            return URI.create(internalDownloadBaseUrl + uri.getRawPath()
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
        }
        return uri;
    }

    private Map<String, Object> document(UUID projectId, ProjectAsset asset) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", "docx");
        document.put("key", documentKey(asset));
        document.put("title", asset.getOriginalFilename());
        document.put("url", signedUrl("/onlyoffice/projects/" + projectId + "/document",
                accessTokenService.documentToken(projectId)));
        return document;
    }

    private Map<String, Object> editor(UUID projectId) {
        Map<String, Object> editor = new LinkedHashMap<>();
        editor.put("callbackUrl", signedUrl("/onlyoffice/projects/" + projectId + "/callback",
                accessTokenService.callbackToken(projectId)));
        editor.put("mode", "edit");
        return editor;
    }

    private String signedUrl(String path, String token) {
        return UriComponentsBuilder.fromUriString(documentBaseUrl + path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String documentKey(ProjectAsset asset) {
        return asset.getId() + "-" + asset.getUpdatedAt().toInstant().toEpochMilli() + "-" + asset.getSizeBytes();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean sameOrigin(URI left, URI right) {
        int leftPort = left.getPort() == -1 ? defaultPort(left.getScheme()) : left.getPort();
        int rightPort = right.getPort() == -1 ? defaultPort(right.getScheme()) : right.getPort();
        return String.valueOf(left.getScheme()).equalsIgnoreCase(String.valueOf(right.getScheme()))
                && String.valueOf(left.getHost()).equalsIgnoreCase(String.valueOf(right.getHost()))
                && leftPort == rightPort;
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
