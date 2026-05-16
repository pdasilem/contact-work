package com.pdasilem.contactwork.contact;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactCustomFieldRepository extends JpaRepository<ContactCustomField, UUID> {
    List<ContactCustomField> findByProjectIdAndContactId(UUID projectId, UUID contactId);
}
