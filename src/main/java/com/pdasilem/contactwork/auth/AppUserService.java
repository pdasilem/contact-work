package com.pdasilem.contactwork.auth;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    public AppUserService(
            AppUserRepository appUserRepository,
            ProjectRepository projectRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService
    ) {
        this.appUserRepository = appUserRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
    }

    public List<AppUser> findAll() {
        currentUserService.requireAdmin();
        return appUserRepository.findAllByOrderByLoginAsc();
    }

    @Transactional
    public AppUser saveUser(
            UUID userId,
            String name,
            String login,
            String password,
            String email,
            AppRole role,
            boolean active,
            Set<UUID> assignedProjectIds
    ) {
        currentUserService.requireAdmin();
        String normalizedLogin = required(login, "Login").toLowerCase();
        AppUser user = userId == null ? new AppUser() : appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        appUserRepository.findByLoginIgnoreCase(normalizedLogin)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Login already exists");
                });
        user.setName(required(name, "Name"));
        user.setLogin(normalizedLogin);
        if (userId == null || password != null && !password.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(required(password, "Password")));
        }
        user.setEmail(trimToNull(email));
        user.setRole(role == null ? AppRole.USER : role);
        user.setActive(active);
        user.setAssignedProjects(loadProjects(assignedProjectIds));
        return appUserRepository.save(user);
    }

    @Transactional
    public void setActive(UUID userId, boolean active) {
        currentUserService.requireAdmin();
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setActive(active);
        appUserRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        currentUserService.requireAdmin();
        appUserRepository.deleteById(userId);
    }

    private Set<Project> loadProjects(Set<UUID> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(projectRepository.findAllById(projectIds));
    }

    private String required(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
