package com.pdasilem.contactwork.inbox;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSyncStateRepository extends JpaRepository<MailSyncState, UUID> {
}
