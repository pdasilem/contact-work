package com.pdasilem.contactwork.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {
    List<AiChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
