package com.pdasilem.contactwork.contact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {
    boolean existsByEmail(String email);
    Optional<Contact> findByEmail(String email);
    Optional<Contact> findByOutboundMessageId(String outboundMessageId);
    List<Contact> findByStatusOrderByCreatedAtAsc(ContactStatus status);
    long countByStatus(ContactStatus status);
}
