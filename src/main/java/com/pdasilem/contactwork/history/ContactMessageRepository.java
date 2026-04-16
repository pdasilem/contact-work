package com.pdasilem.contactwork.history;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
    List<ContactMessage> findByContactIdOrderByMessageTimestampAsc(UUID contactId);
    Optional<ContactMessage> findByMessageId(String messageId);
}
