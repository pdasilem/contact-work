package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.asset.ProjectAsset;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.project.asset.ProjectAssetType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets")
public class ProjectAssetController {

    private final ProjectAssetService projectAssetService;

    public ProjectAssetController(ProjectAssetService projectAssetService) {
        this.projectAssetService = projectAssetService;
    }

    @GetMapping
    public List<ProjectAssetResponse> list(@PathVariable UUID projectId) {
        return projectAssetService.activeAssets(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectAssetResponse upload(
            @PathVariable UUID projectId,
            @RequestParam ProjectAssetType type,
            @RequestParam MultipartFile file
    ) throws java.io.IOException {
        return toResponse(projectAssetService.store(
                projectId,
                type,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream()
        ));
    }

    @DeleteMapping("/{assetId}")
    public void delete(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        projectAssetService.delete(projectId, assetId);
    }

    private ProjectAssetResponse toResponse(ProjectAsset asset) {
        return new ProjectAssetResponse(
                asset.getId(),
                asset.getType(),
                asset.getOriginalFilename(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.isActive(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
