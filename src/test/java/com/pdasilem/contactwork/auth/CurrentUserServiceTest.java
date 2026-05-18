package com.pdasilem.contactwork.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserServiceTest {

    private final AppUserRepository appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
    private final CurrentUserService currentUserService = new CurrentUserService(appUserRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanAccessAnyProject() {
        authenticate("admin");
        when(appUserRepository.findByLoginIgnoreCase("admin")).thenReturn(java.util.Optional.of(user(AppRole.ADMIN, true)));

        assertThat(currentUserService.canAccessProject(project(ProjectStatus.NEW))).isTrue();
    }

    @Test
    void anonymousUserCannotAccessProject() {
        Project project = project(ProjectStatus.ACTIVE);

        assertThat(currentUserService.canAccessProject(project)).isFalse();
        assertThat(currentUserService.canAccessProjectId(project.getId())).isFalse();
        assertThat(currentUserService.canCreateProjects()).isFalse();
    }

    @Test
    void userCanAccessOnlyAssignedActiveProject() {
        Project assigned = project(ProjectStatus.ACTIVE);
        Project unassigned = project(ProjectStatus.ACTIVE);
        Project inactiveAssigned = project(ProjectStatus.NEW);
        authenticate("user");
        AppUser user = user(AppRole.USER, true);
        user.setAssignedProjects(Set.of(assigned, inactiveAssigned));
        when(appUserRepository.findByLoginIgnoreCase("user")).thenReturn(java.util.Optional.of(user));

        assertThat(currentUserService.canAccessProject(assigned)).isTrue();
        assertThat(currentUserService.canAccessProject(unassigned)).isFalse();
        assertThat(currentUserService.canAccessProject(inactiveAssigned)).isFalse();
        assertThat(currentUserService.canAccessProjectId(assigned.getId())).isTrue();
        assertThat(currentUserService.canAccessProjectId(unassigned.getId())).isFalse();
        assertThat(currentUserService.canAccessProjectId(inactiveAssigned.getId())).isFalse();
    }

    @Test
    void inactiveUserCannotAccessAssignedProject() {
        Project assigned = project(ProjectStatus.ACTIVE);
        authenticate("user");
        AppUser user = user(AppRole.USER, false);
        user.setAssignedProjects(Set.of(assigned));
        when(appUserRepository.findByLoginIgnoreCase("user")).thenReturn(java.util.Optional.of(user));

        assertThat(currentUserService.canAccessProject(assigned)).isFalse();
    }

    @Test
    void missingAuthenticatedUserCannotAccessProject() {
        authenticate("deleted-user");
        when(appUserRepository.findByLoginIgnoreCase("deleted-user")).thenReturn(java.util.Optional.empty());

        assertThat(currentUserService.canAccessProject(project(ProjectStatus.ACTIVE))).isFalse();
    }

    @Test
    void filterVisibleProjectsReturnsOnlyAssignedActiveProjectsForUser() {
        Project assigned = project(ProjectStatus.ACTIVE);
        Project unassigned = project(ProjectStatus.ACTIVE);
        Project inactiveAssigned = project(ProjectStatus.NEW);
        authenticate("user");
        AppUser user = user(AppRole.USER, true);
        user.setAssignedProjects(Set.of(assigned, inactiveAssigned));
        when(appUserRepository.findByLoginIgnoreCase("user")).thenReturn(java.util.Optional.of(user));

        assertThat(currentUserService.filterVisibleProjects(List.of(assigned, unassigned, inactiveAssigned)))
                .containsExactly(assigned);
        verify(appUserRepository, times(1)).findByLoginIgnoreCase("user");
    }

    @Test
    void filterVisibleProjectsReturnsAllProjectsForActiveAdmin() {
        Project active = project(ProjectStatus.ACTIVE);
        Project inactive = project(ProjectStatus.NEW);
        authenticate("admin");
        when(appUserRepository.findByLoginIgnoreCase("admin")).thenReturn(java.util.Optional.of(user(AppRole.ADMIN, true)));

        assertThat(currentUserService.filterVisibleProjects(List.of(active, inactive)))
                .containsExactly(active, inactive);
    }

    @Test
    void filterVisibleProjectsReturnsNoProjectsForInactiveOrMissingUsers() {
        Project active = project(ProjectStatus.ACTIVE);
        authenticate("inactive");
        when(appUserRepository.findByLoginIgnoreCase("inactive"))
                .thenReturn(java.util.Optional.of(user(AppRole.USER, false)));

        assertThat(currentUserService.filterVisibleProjects(List.of(active))).isEmpty();

        authenticate("missing");
        when(appUserRepository.findByLoginIgnoreCase("missing")).thenReturn(java.util.Optional.empty());

        assertThat(currentUserService.filterVisibleProjects(List.of(active))).isEmpty();
    }

    private void authenticate(String login) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, "password", java.util.List.of())
        );
    }

    private AppUser user(AppRole role, boolean active) {
        AppUser user = new AppUser();
        user.setLogin(role.name().toLowerCase());
        user.setName(role.name());
        user.setRole(role);
        user.setActive(active);
        user.setPasswordHash("hash");
        return user;
    }

    private Project project(ProjectStatus status) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setName("Project");
        project.setStatus(status);
        return project;
    }
}
