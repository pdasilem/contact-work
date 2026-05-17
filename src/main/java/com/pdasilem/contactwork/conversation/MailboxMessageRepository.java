package com.pdasilem.contactwork.conversation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxMessageRepository extends JpaRepository<MailboxMessage, UUID> {
    boolean existsByProjectIdAndNormalizedMessageId(UUID projectId, String normalizedMessageId);
    boolean existsByProjectIdAndContentHashAndNormalizedMessageIdIsNull(UUID projectId, String contentHash);
    List<MailboxMessage> findByProjectIdAndContactIdOrderByServiceDateAsc(UUID projectId, UUID contactId);
    List<MailboxMessage> findTop20ByProjectIdOrderByServiceDateDesc(UUID projectId);
}
