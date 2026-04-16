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

    public List<Contact> findContacts(ContactStatus status, String email, String organization) {
        Specification<Contact> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
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

    public Contact getContact(UUID id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + id));
    }

    public Contact save(Contact contact) {
        return contactRepository.save(contact);
    }

    public Contact findByEmail(String email) {
        return contactRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found by email: " + email));
    }

    public long countByStatus(ContactStatus status) {
        return contactRepository.countByStatus(status);
    }
}
