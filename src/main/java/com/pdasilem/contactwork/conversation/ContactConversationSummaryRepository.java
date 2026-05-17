package com.pdasilem.contactwork.conversation;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactConversationSummaryRepository extends JpaRepository<ContactConversationSummary, UUID> {
    Optional<ContactConversationSummary> findByContactId(UUID contactId);
}
