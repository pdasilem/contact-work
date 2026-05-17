package com.pdasilem.contactwork.onlyoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class OnlyOfficeController {

    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final OnlyOfficeEditorService onlyOfficeEditorService;
    private final ObjectMapper objectMapper;

    public OnlyOfficeController(OnlyOfficeEditorService onlyOfficeEditorService, ObjectMapper objectMapper) {
        this.onlyOfficeEditorService = onlyOfficeEditorService;
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
    public ResponseEntity<byte[]> document(@PathVariable UUID projectId) {
        ProjectAsset asset = onlyOfficeEditorService.activeLetter(projectId);
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(asset.getOriginalFilename())
                        .build().toString())
                .body(onlyOfficeEditorService.activeLetterBytes(projectId));
    }

    @PostMapping("/onlyoffice/projects/{projectId}/callback")
    @ResponseBody
    public Map<String, Integer> callback(@PathVariable UUID projectId, @org.springframework.web.bind.annotation.RequestBody OnlyOfficeCallbackRequest request) {
        onlyOfficeEditorService.handleCallback(projectId, request);
        return Map.of("error", 0);
    }
}
