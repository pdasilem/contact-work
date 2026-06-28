package com.pdasilem.contactwork.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {
    boolean existsByProjectIdAndEmail(UUID projectId, String email);
    Optional<Contact> findByProjectIdAndId(UUID projectId, UUID id);
    Optional<Contact> findByProjectIdAndEmail(UUID projectId, String email);
    Optional<Contact> findByProjectIdAndOutboundMessageId(UUID projectId, String outboundMessageId);
    List<Contact> findByProjectIdAndDeletedAtIsNull(UUID projectId);
    List<Contact> findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(UUID projectId, ContactStatus status);
    List<Contact> findByProjectIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(UUID projectId, List<ContactStatus> statuses);
    long countByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, ContactStatus status);
}
