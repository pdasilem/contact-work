package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.project.ProjectService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    public ProjectController(ProjectService projectService, ProjectMapper projectMapper) {
        this.projectService = projectService;
        this.projectMapper = projectMapper;
    }

    @GetMapping
    public List<ProjectResponse> getProjects() {
        return projectService.findAll().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @PostMapping
    public ProjectResponse createProject(@RequestBody ProjectRequest request) {
        return projectMapper.toResponse(projectService.create(projectMapper.toEntity(request)));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse getProject(@PathVariable UUID projectId) {
        return projectMapper.toResponse(projectService.getProject(projectId));
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse updateProject(@PathVariable UUID projectId, @RequestBody ProjectRequest request) {
        return projectMapper.toResponse(projectService.update(projectId, projectMapper.toEntity(request)));
    }
}
