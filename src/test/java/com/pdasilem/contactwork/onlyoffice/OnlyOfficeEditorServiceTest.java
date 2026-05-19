package com.pdasilem.contactwork.onlyoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.web.client.RestClient;

class OnlyOfficeEditorServiceTest {

    @Test
    void editorConfigUsesActiveLetterAssetForEditMode() throws Exception {
        UUID projectId = UUID.randomUUID();
        ProjectAsset asset = activeLetter(projectId);
        ProjectAssetService projectAssetService = Mockito.mock(ProjectAssetService.class);
        when(projectAssetService.activeLetterOrThrow(projectId)).thenReturn(asset);
        OnlyOfficeAccessTokenService accessTokenService = accessTokenService();
        OnlyOfficeEditorService service = new OnlyOfficeEditorService(
                projectAssetService,
                accessTokenService,
                "http://browser-onlyoffice",
                "http://app:8083",
                "http://onlyoffice"
        );

        Map<String, Object> config = service.editorConfig(projectId);

        assertThat(config).containsEntry("documentType", "word");
        assertThat(service.apiScriptUrl()).isEqualTo("http://browser-onlyoffice/web-apps/apps/api/documents/api.js");
        Map<?, ?> document = (Map<?, ?>) config.get("document");
        assertThat(document.get("fileType")).isEqualTo("docx");
        assertThat(document.get("title")).isEqualTo("letter.docx");
        assertThat(document.get("key").toString()).contains(asset.getId().toString());
        assertThat(document.get("url")).isEqualTo("http://app:8083/onlyoffice/projects/" + projectId
                + "/document?token=" + accessTokenService.documentToken(projectId));
        Map<?, ?> editor = (Map<?, ?>) config.get("editorConfig");
        assertThat(editor.get("mode")).isEqualTo("edit");
        assertThat(editor.get("callbackUrl")).isEqualTo("http://app:8083/onlyoffice/projects/" + projectId
                + "/callback?token=" + accessTokenService.callbackToken(projectId));
    }

    @Test
    void callbackDownloadUrlRewritesPublicOnlyofficeOriginToInternalOrigin() {
        OnlyOfficeEditorService service = new OnlyOfficeEditorService(
                Mockito.mock(ProjectAssetService.class),
                accessTokenService(),
                "http://127.0.0.1:8084",
                "http://app:8083",
                "http://onlyoffice"
        );

        assertThat(service.rewriteDownloadUri("http://127.0.0.1:8084/cache/files/file.docx?md5=abc").toString())
                .isEqualTo("http://onlyoffice/cache/files/file.docx?md5=abc");
    }

    @Test
    void callbackStatusTwoAndSixDownloadAndOverwriteActiveLetter() throws Exception {
        UUID projectId = UUID.randomUUID();
        byte[] updated = "updated docx".getBytes();
        RestClient restClient = Mockito.mock(RestClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(restClient.get()
                .uri(URI.create("http://internal-onlyoffice/cache/files/file.docx?md5=abc"))
                .retrieve()
                .body(byte[].class)).thenReturn(updated);
        when(restClient.get()
                .uri(URI.create("http://internal-onlyoffice/cache/files/file.docx?md5=def"))
                .retrieve()
                .body(byte[].class)).thenReturn(updated);
        ProjectAssetService projectAssetService = Mockito.mock(ProjectAssetService.class);
        RestClient.Builder builder = Mockito.mock(RestClient.Builder.class);
        try (MockedStatic<RestClient> restClients = Mockito.mockStatic(RestClient.class)) {
            restClients.when(RestClient::builder).thenReturn(builder);
            when(builder.build()).thenReturn(restClient);
            OnlyOfficeEditorService service = new OnlyOfficeEditorService(
                    projectAssetService,
                    accessTokenService(),
                    "http://browser-onlyoffice",
                    "http://app:8083",
                    "http://internal-onlyoffice"
            );

            service.handleCallback(projectId,
                    new OnlyOfficeCallbackRequest(2, "http://browser-onlyoffice/cache/files/file.docx?md5=abc"));
            service.handleCallback(projectId,
                    new OnlyOfficeCallbackRequest(6, "http://browser-onlyoffice/cache/files/file.docx?md5=def"));
        }

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(projectAssetService, times(2)).overwriteActiveLetter(eq(projectId), bytes.capture());
        assertThat(bytes.getAllValues()).allSatisfy(value -> assertThat(value).isEqualTo(updated));
    }

    private OnlyOfficeAccessTokenService accessTokenService() {
        return new OnlyOfficeAccessTokenService("test-onlyoffice-secret");
    }

    private ProjectAsset activeLetter(UUID projectId) throws Exception {
        Project project = new Project();
        project.setId(projectId);
        ProjectAsset asset = new ProjectAsset();
        asset.setId(UUID.randomUUID());
        asset.setProject(project);
        asset.setType(ProjectAssetType.LETTER_TEMPLATE);
        asset.setOriginalFilename("letter.docx");
        asset.setStoredPath("/tmp/letter.docx");
        asset.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        asset.setSizeBytes(42);
        asset.setActive(true);
        Field updatedAt = ProjectAsset.class.getDeclaredField("updatedAt");
        updatedAt.setAccessible(true);
        updatedAt.set(asset, OffsetDateTime.now());
        return asset;
    }
}
