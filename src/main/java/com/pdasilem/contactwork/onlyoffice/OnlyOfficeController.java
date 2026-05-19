package com.pdasilem.contactwork.onlyoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class OnlyOfficeController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final OnlyOfficeEditorService onlyOfficeEditorService;
    private final OnlyOfficeAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    public OnlyOfficeController(
            OnlyOfficeEditorService onlyOfficeEditorService,
            OnlyOfficeAccessTokenService accessTokenService,
            ObjectMapper objectMapper
    ) {
        this.onlyOfficeEditorService = onlyOfficeEditorService;
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/onlyoffice/projects/{projectId}/editor", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String editor(@PathVariable UUID projectId) throws JsonProcessingException {
        Map<String, Object> config = onlyOfficeEditorService.editorConfig(projectId);
        return """
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <title>ContactWork Letter Editor</title>
                  <script src="%s"></script>
                  <style>
                    html, body, #editor { width: 100%%; height: 100%%; margin: 0; }
                  </style>
                </head>
                <body>
                  <div id="editor"></div>
                  <script>
                    const config = %s;
                    window.docEditor = new DocsAPI.DocEditor("editor", config);
                  </script>
                </body>
                </html>
                """.formatted(onlyOfficeEditorService.apiScriptUrl(), objectMapper.writeValueAsString(config));
    }

    @GetMapping("/onlyoffice/projects/{projectId}/document")
    public ResponseEntity<byte[]> document(@PathVariable UUID projectId, @RequestParam(required = false) String token) {
        requireValidToken(accessTokenService.isValidDocumentToken(projectId, token));
        ProjectAsset asset = onlyOfficeEditorService.activeLetterForSystem(projectId);
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.getOriginalFilename())
                        .build().toString())
                .body(onlyOfficeEditorService.activeLetterBytesForSystem(projectId));
    }

    @PostMapping("/onlyoffice/projects/{projectId}/callback")
    @ResponseBody
    public Map<String, Integer> callback(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String token,
            @org.springframework.web.bind.annotation.RequestBody OnlyOfficeCallbackRequest request
    ) {
        requireValidToken(accessTokenService.isValidCallbackToken(projectId, token));
        onlyOfficeEditorService.handleCallback(projectId, request);
        return Map.of("error", 0);
    }

    private void requireValidToken(boolean valid) {
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
