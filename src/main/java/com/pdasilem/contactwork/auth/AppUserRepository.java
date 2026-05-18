package com.pdasilem.contactwork.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    @EntityGraph(attributePaths = "assignedProjects")
    Optional<AppUser> findByLoginIgnoreCase(String login);

    boolean existsByLoginIgnoreCase(String login);

    @EntityGraph(attributePaths = "assignedProjects")
    List<AppUser> findAllByOrderByLoginAsc();
}
