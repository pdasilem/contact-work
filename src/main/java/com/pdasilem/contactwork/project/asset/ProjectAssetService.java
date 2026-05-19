package com.pdasilem.contactwork.project.asset;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAssetService {
    private static final Logger log = LoggerFactory.getLogger(ProjectAssetService.class);

    private final ProjectAssetRepository projectAssetRepository;
    private final ProjectService projectService;
    private final AppProperties appProperties;

    public ProjectAssetService(
            ProjectAssetRepository projectAssetRepository,
            ProjectService projectService,
            AppProperties appProperties
    ) {
        this.projectAssetRepository = projectAssetRepository;
        this.projectService = projectService;
        this.appProperties = appProperties;
    }

    @Transactional
    public ProjectAsset store(UUID projectId, ProjectAssetType type, String filename, String contentType, InputStream inputStream) {
        Project project = projectService.getProject(projectId);
        String cleanFilename = sanitize(filename);
        try {
            Path projectDir = Files.createDirectories(Path.of(appProperties.resources().workingDir())
                    .resolve("projects")
                    .resolve(projectId.toString()));
            Path storedPath = projectDir.resolve(UUID.randomUUID() + "-" + cleanFilename);
            long size = Files.copy(inputStream, storedPath);
            if (type == ProjectAssetType.LETTER_TEMPLATE) {
                activeLetter(projectId).ifPresent(existing -> delete(projectId, existing));
            }
            ProjectAsset asset = new ProjectAsset();
            asset.setProject(project);
            asset.setType(type);
            asset.setOriginalFilename(cleanFilename);
            asset.setStoredPath(storedPath.toString());
            asset.setContentType(contentType);
            asset.setSizeBytes(size);
            asset.setActive(true);
            return projectAssetRepository.save(asset);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store project asset", ex);
        }
    }

    public List<ProjectAsset> activeAssets(UUID projectId) {
        projectService.getProject(projectId);
        return findActiveAssets(projectId);
    }

    public java.util.Optional<ProjectAsset> activeLetter(UUID projectId) {
        projectService.getProject(projectId);
        return findActiveLetter(projectId);
    }

    public java.util.Optional<ProjectAsset> activeLetterForSystem(UUID projectId) {
        projectService.getProjectForSystem(projectId);
        return findActiveLetter(projectId);
    }

    public ProjectAsset activeLetterOrThrow(UUID projectId) {
        return activeLetter(projectId)
                .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
    }

    public ProjectAsset activeLetterOrThrowForSystem(UUID projectId) {
        return activeLetterForSystem(projectId)
                .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
    }

    public List<ProjectAsset> activeAttachments(UUID projectId) {
        projectService.getProject(projectId);
        return findActiveAttachments(projectId);
    }

    public Resource activeLetterResource(UUID projectId) {
        ProjectAsset asset = activeLetterOrThrow(projectId);
        return new PathResource(Path.of(asset.getStoredPath()));
    }

    public Resource activeLetterResourceForSystem(UUID projectId) {
        ProjectAsset asset = activeLetterOrThrowForSystem(projectId);
        return new PathResource(Path.of(asset.getStoredPath()));
    }

    @Transactional
    public ProjectAsset overwriteActiveLetter(UUID projectId, byte[] bytes) {
        return overwriteActiveLetter(projectId, bytes, false);
    }

    @Transactional
    public ProjectAsset overwriteActiveLetterForSystem(UUID projectId, byte[] bytes) {
        return overwriteActiveLetter(projectId, bytes, true);
    }

    private ProjectAsset overwriteActiveLetter(UUID projectId, byte[] bytes, boolean systemAccess) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Updated letter template is empty");
        }
        ProjectAsset asset = systemAccess ? activeLetterOrThrowForSystem(projectId) : activeLetterOrThrow(projectId);
        try {
            Files.write(Path.of(asset.getStoredPath()), bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to overwrite active letter template", ex);
        }
        asset.setSizeBytes(bytes.length);
        return projectAssetRepository.save(asset);
    }

    public List<MailAttachment> activeMailAttachments(UUID projectId) {
        return activeAttachments(projectId).stream()
                .filter(asset -> {
                    Path path = Path.of(asset.getStoredPath());
                    boolean exists = Files.exists(path);
                    if (!exists) {
                        log.warn(
                                "Skipping missing optional mail attachment: projectId={} assetId={} filename={} path={}",
                                projectId,
                                asset.getId(),
                                asset.getOriginalFilename(),
                                asset.getStoredPath()
                        );
                    }
                    return exists;
                })
                .map(asset -> new MailAttachment(asset.getOriginalFilename(), new PathResource(Path.of(asset.getStoredPath()))))
                .toList();
    }

    public List<MailAttachment> activeMailAttachmentsForSystem(UUID projectId) {
        projectService.getProjectForSystem(projectId);
        return findActiveAttachments(projectId).stream()
                .filter(asset -> {
                    Path path = Path.of(asset.getStoredPath());
                    boolean exists = Files.exists(path);
                    if (!exists) {
                        log.warn(
                                "Skipping missing optional mail attachment: projectId={} assetId={} filename={} path={}",
                                projectId,
                                asset.getId(),
                                asset.getOriginalFilename(),
                                asset.getStoredPath()
                        );
                    }
                    return exists;
                })
                .map(asset -> new MailAttachment(asset.getOriginalFilename(), new PathResource(Path.of(asset.getStoredPath()))))
                .toList();
    }

    private List<ProjectAsset> findActiveAssets(UUID projectId) {
        return projectAssetRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAsc(projectId);
    }

    private java.util.Optional<ProjectAsset> findActiveLetter(UUID projectId) {
        return projectAssetRepository.findFirstByProjectIdAndTypeAndActiveTrueOrderByCreatedAtDesc(
                projectId, ProjectAssetType.LETTER_TEMPLATE);
    }

    private List<ProjectAsset> findActiveAttachments(UUID projectId) {
        return projectAssetRepository.findByProjectIdAndTypeAndActiveTrueOrderByCreatedAtAsc(
                projectId, ProjectAssetType.ATTACHMENT);
    }

    @Transactional
    public void delete(UUID projectId, UUID assetId) {
        projectService.getProject(projectId);
        ProjectAsset asset = projectAssetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Project asset not found: " + assetId));
        delete(projectId, asset);
    }

    private void delete(UUID projectId, ProjectAsset asset) {
        if (!asset.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Project asset not found in project " + projectId + ": " + asset.getId());
        }
        try {
            Files.deleteIfExists(Path.of(asset.getStoredPath()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete project asset file", ex);
        }
        projectAssetRepository.delete(asset);
    }

    private String sanitize(String filename) {
        String value = filename == null || filename.isBlank() ? "upload.bin" : filename.trim();
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
