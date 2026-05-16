package com.pdasilem.contactwork.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectContactColumnRepository extends JpaRepository<ProjectContactColumn, UUID> {
    List<ProjectContactColumn> findByProjectIdOrderByColumnOrderAsc(UUID projectId);
    Optional<ProjectContactColumn> findByProjectIdAndColumnKey(UUID projectId, String columnKey);
    int countByProjectId(UUID projectId);
}
