package com.pdasilem.contactwork.auth;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> currentUser() {
        if (!hasAuthenticatedPrincipal()) {
            return Optional.empty();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return appUserRepository.findByLoginIgnoreCase(authentication.getName());
    }

    @Transactional(readOnly = true)
    public boolean isAdmin() {
        return currentUser()
                .map(user -> user.isActive() && user.getRole() == AppRole.ADMIN)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canCreateProjects() {
        return currentUser()
                .map(user -> user.isActive() && user.getRole() == AppRole.ADMIN)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canUseGlobalSettings() {
        return isAdmin();
    }

    @Transactional(readOnly = true)
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    @Transactional(readOnly = true)
    public boolean canAccessProject(Project project) {
        Optional<AppUser> current = currentUser();
        if (current.isEmpty()) {
            return false;
        }
        return canAccessProject(current.get(), project);
    }

    private boolean canAccessProject(AppUser user, Project project) {
        if (!user.isActive()) {
            return false;
        }
        if (user.getRole() == AppRole.ADMIN) {
            return true;
        }
        return project.getStatus() == ProjectStatus.ACTIVE
                && user.getAssignedProjects().stream()
                .map(Project::getId)
                .anyMatch(project.getId()::equals);
    }

    @Transactional(readOnly = true)
    public void requireProjectAccess(Project project) {
        if (!canAccessProject(project)) {
            throw new AccessDeniedException("Project access denied");
        }
    }

    @Transactional(readOnly = true)
    public List<Project> filterVisibleProjects(List<Project> projects) {
        Optional<AppUser> current = currentUser();
        if (current.isEmpty()) {
            return List.of();
        }
        AppUser user = current.get();
        return projects.stream()
                .filter(project -> canAccessProject(user, project))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canAccessProjectId(UUID projectId) {
        return currentUser()
                .map(user -> {
                    if (!user.isActive()) {
                        return false;
                    }
                    if (user.getRole() == AppRole.ADMIN) {
                        return true;
                    }
                    return user.getAssignedProjects().stream()
                            .anyMatch(project -> projectId.equals(project.getId())
                                    && project.getStatus() == ProjectStatus.ACTIVE);
                })
                .orElse(false);
    }

    private boolean hasAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated();
    }
}
