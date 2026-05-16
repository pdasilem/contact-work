package com.pdasilem.contactwork.contact;

import com.pdasilem.contactwork.project.Project;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProjectContactColumnService {

    private final ProjectContactColumnRepository projectContactColumnRepository;

    public ProjectContactColumnService(ProjectContactColumnRepository projectContactColumnRepository) {
        this.projectContactColumnRepository = projectContactColumnRepository;
    }

    public List<ProjectContactColumn> findVisibleColumns(UUID projectId) {
        return projectContactColumnRepository.findByProjectIdOrderByColumnOrderAsc(projectId).stream()
                .filter(ProjectContactColumn::isVisible)
                .toList();
    }

    public void ensureColumn(Project project, String key, String label, ContactColumnSource sourceType, int fallbackOrder) {
        projectContactColumnRepository.findByProjectIdAndColumnKey(project.getId(), key).orElseGet(() -> {
            ProjectContactColumn column = new ProjectContactColumn();
            column.setId(UUID.randomUUID());
            column.setProject(project);
            column.setColumnKey(key);
            column.setDisplayLabel(label);
            column.setSourceType(sourceType);
            column.setColumnOrder(Math.max(fallbackOrder, projectContactColumnRepository.countByProjectId(project.getId())));
            column.setVisible(true);
            return projectContactColumnRepository.save(column);
        });
    }
}
