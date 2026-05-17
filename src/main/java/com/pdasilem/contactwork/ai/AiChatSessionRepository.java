package com.pdasilem.contactwork.ai;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {
    Optional<AiChatSession> findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope scope, UUID projectId);
    Optional<AiChatSession> findFirstByScopeAndContactIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope scope, UUID contactId);
    java.util.List<AiChatSession> findByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope scope, UUID projectId);
    java.util.List<AiChatSession> findByScopeAndContactIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope scope, UUID contactId);
}
