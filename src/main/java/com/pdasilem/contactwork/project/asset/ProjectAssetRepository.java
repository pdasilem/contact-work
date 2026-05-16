package com.pdasilem.contactwork.project.asset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAssetRepository extends JpaRepository<ProjectAsset, UUID> {
    List<ProjectAsset> findByProjectIdAndTypeAndActiveTrueOrderByCreatedAtAsc(UUID projectId, ProjectAssetType type);
    Optional<ProjectAsset> findFirstByProjectIdAndTypeAndActiveTrueOrderByCreatedAtDesc(UUID projectId, ProjectAssetType type);
    List<ProjectAsset> findByProjectIdAndActiveTrueOrderByCreatedAtAsc(UUID projectId);
}
