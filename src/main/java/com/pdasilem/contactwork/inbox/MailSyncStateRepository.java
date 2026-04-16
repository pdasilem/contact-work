package com.pdasilem.contactwork.inbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSyncStateRepository extends JpaRepository<MailSyncState, Short> {
}
