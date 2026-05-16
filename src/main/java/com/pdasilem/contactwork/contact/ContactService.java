package com.pdasilem.contactwork.contact;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public List<Contact> findContacts(UUID projectId, ContactStatus status, String email, String organization) {
        Specification<Contact> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), projectId));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }
            if (organization != null && !organization.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("organizationName")), "%" + organization.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return contactRepository.findAll(specification);
    }

    public List<Contact> searchContacts(UUID projectId, String search) {
        Specification<Contact> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), projectId));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("organizationName")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("contactName")), pattern)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        return contactRepository.findAll(specification);
    }

    public Contact getContact(UUID projectId, UUID id) {
        return contactRepository.findByProjectIdAndId(projectId, id)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + id));
    }

    public Contact save(Contact contact) {
        return contactRepository.save(contact);
    }

    public Contact findByEmail(UUID projectId, String email) {
        return contactRepository.findByProjectIdAndEmail(projectId, email)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found by email in project " + projectId + ": " + email));
    }

    public long countByStatus(UUID projectId, ContactStatus status) {
        return contactRepository.countByProjectIdAndStatus(projectId, status);
    }
}
