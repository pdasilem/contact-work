package com.pdasilem.contactwork.project.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectAssetServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ProjectAssetRepository projectAssetRepository;

    @Mock
    private ProjectService projectService;

    @Test
    void storesLetterTemplatesAndAttachmentsAsActiveProjectAssets() throws Exception {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Project");

        ProjectAssetService service = new ProjectAssetService(projectAssetRepository, projectService, appProperties());
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(projectAssetRepository.findFirstByProjectIdAndTypeAndActiveTrueOrderByCreatedAtDesc(
                project.getId(), ProjectAssetType.LETTER_TEMPLATE)).thenReturn(Optional.empty());
        when(projectAssetRepository.save(any(ProjectAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAsset letter = service.store(
                project.getId(),
                ProjectAssetType.LETTER_TEMPLATE,
                "Letter.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new ByteArrayInputStream("letter".getBytes(StandardCharsets.UTF_8))
        );
        ProjectAsset attachment = service.store(
                project.getId(),
                ProjectAssetType.ATTACHMENT,
                "Pitch_deck_en.pdf",
                "application/pdf",
                new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(letter.getType()).isEqualTo(ProjectAssetType.LETTER_TEMPLATE);
        assertThat(letter.getOriginalFilename()).isEqualTo("Letter.docx");
        assertThat(letter.isActive()).isTrue();
        assertThat(Files.readString(Path.of(letter.getStoredPath()))).isEqualTo("letter");

        assertThat(attachment.getType()).isEqualTo(ProjectAssetType.ATTACHMENT);
        assertThat(attachment.getOriginalFilename()).isEqualTo("Pitch_deck_en.pdf");
        assertThat(attachment.isActive()).isTrue();
        assertThat(Files.readString(Path.of(attachment.getStoredPath()))).isEqualTo("pdf");
    }

    @Test
    void hardDeleteRemovesStoredFileAndDatabaseRow() throws Exception {
        Project project = project();
        ProjectAsset asset = asset(project, ProjectAssetType.ATTACHMENT, "deck.pdf", "pdf");
        ProjectAssetService service = new ProjectAssetService(projectAssetRepository, projectService, appProperties());
        when(projectAssetRepository.findById(asset.getId())).thenReturn(Optional.of(asset));

        service.delete(project.getId(), asset.getId());

        assertThat(Files.exists(Path.of(asset.getStoredPath()))).isFalse();
        verify(projectAssetRepository).delete(asset);
    }

    @Test
    void replacingLetterTemplateDeletesPreviousTemplateFileAndRow() throws Exception {
        Project project = project();
        ProjectAsset previous = asset(project, ProjectAssetType.LETTER_TEMPLATE, "old.docx", "old");
        ProjectAssetService service = new ProjectAssetService(projectAssetRepository, projectService, appProperties());
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(projectAssetRepository.findFirstByProjectIdAndTypeAndActiveTrueOrderByCreatedAtDesc(
                project.getId(), ProjectAssetType.LETTER_TEMPLATE)).thenReturn(Optional.of(previous));
        when(projectAssetRepository.save(any(ProjectAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAsset replacement = service.store(
                project.getId(),
                ProjectAssetType.LETTER_TEMPLATE,
                "new.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new ByteArrayInputStream("new".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(Files.exists(Path.of(previous.getStoredPath()))).isFalse();
        assertThat(Files.readString(Path.of(replacement.getStoredPath()))).isEqualTo("new");
        verify(projectAssetRepository).delete(previous);
    }

    @Test
    void attachmentUploadDoesNotDeleteExistingAttachments() throws Exception {
        Project project = project();
        ProjectAssetService service = new ProjectAssetService(projectAssetRepository, projectService, appProperties());
        when(projectService.getProject(project.getId())).thenReturn(project);
        when(projectAssetRepository.save(any(ProjectAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAsset attachment = service.store(
                project.getId(),
                ProjectAssetType.ATTACHMENT,
                "deck.pdf",
                "application/pdf",
                new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8))
        );

        assertThat(Files.readString(Path.of(attachment.getStoredPath()))).isEqualTo("pdf");
        verify(projectAssetRepository, never()).delete(any(ProjectAsset.class));
    }

    @Test
    void overwriteActiveLetterUpdatesSameAssetAndStoredFile() throws Exception {
        Project project = project();
        ProjectAsset asset = asset(project, ProjectAssetType.LETTER_TEMPLATE, "letter.docx", "old");
        ProjectAssetService service = new ProjectAssetService(projectAssetRepository, projectService, appProperties());
        when(projectAssetRepository.findFirstByProjectIdAndTypeAndActiveTrueOrderByCreatedAtDesc(
                project.getId(), ProjectAssetType.LETTER_TEMPLATE)).thenReturn(Optional.of(asset));
        when(projectAssetRepository.save(any(ProjectAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAsset updated = service.overwriteActiveLetter(project.getId(), "updated".getBytes(StandardCharsets.UTF_8));

        assertThat(updated).isSameAs(asset);
        assertThat(updated.getStoredPath()).isEqualTo(asset.getStoredPath());
        assertThat(updated.getSizeBytes()).isEqualTo(7);
        assertThat(Files.readString(Path.of(asset.getStoredPath()))).isEqualTo("updated");
        verify(projectAssetRepository, never()).delete(any(ProjectAsset.class));
    }

    private Project project() {
        Project project = new Project();
        project.setId(Project.DEFAULT_PROJECT_ID);
        project.setName("Project");
        return project;
    }

    private ProjectAsset asset(Project project, ProjectAssetType type, String filename, String content) throws Exception {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        ProjectAsset asset = new ProjectAsset();
        asset.setId(UUID.randomUUID());
        asset.setProject(project);
        asset.setType(type);
        asset.setOriginalFilename(filename);
        asset.setStoredPath(path.toString());
        asset.setContentType(type == ProjectAssetType.ATTACHMENT ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        asset.setSizeBytes(content.length());
        asset.setActive(true);
        return asset;
    }

    private AppProperties appProperties() {
        return new AppProperties(
                new AppProperties.Resources(tempDir.toString()),
                new AppProperties.Mail(1000, "0 */5 * * * *", null),
                null
        );
    }
}
