package com.pdasilem.contactwork.contact;

import jakarta.persistence.criteria.Predicate;
import com.pdasilem.contactwork.common.EmailUtils;
import com.pdasilem.contactwork.history.ContactMessageRepository;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final ContactCustomFieldRepository contactCustomFieldRepository;
    private final ProjectService projectService;

    public ContactService(
            ContactRepository contactRepository,
            ContactMessageRepository contactMessageRepository,
            ContactCustomFieldRepository contactCustomFieldRepository,
            ProjectService projectService
    ) {
        this.contactRepository = contactRepository;
        this.contactMessageRepository = contactMessageRepository;
        this.contactCustomFieldRepository = contactCustomFieldRepository;
        this.projectService = projectService;
    }

    public List<Contact> findContacts(UUID projectId, ContactStatus status, String email, String organization) {
        projectService.getProject(projectId);
        Specification<Contact> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), projectId));
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
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
        return contactRepository.findAll(searchSpecification(projectId, search));
    }

    public Page<Contact> searchContacts(UUID projectId, String search, Pageable pageable) {
        return contactRepository.findAll(searchSpecification(projectId, search), pageable);
    }

    public long countContacts(UUID projectId, String search) {
        return contactRepository.count(searchSpecification(projectId, search));
    }

    private Specification<Contact> searchSpecification(UUID projectId, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("project").get("id"), projectId));
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
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
    }

    public Contact getContact(UUID projectId, UUID id) {
        projectService.getProject(projectId);
        return contactRepository.findByProjectIdAndId(projectId, id)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + id));
    }

    public Contact save(Contact contact) {
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact createContact(
            Project project,
            String organizationName,
            String contactName,
            String email,
            String note,
            Map<String, String> customFields
    ) {
        String normalizedEmail = requiredEmail(email);
        if (contactRepository.existsByProjectIdAndEmail(project.getId(), normalizedEmail)) {
            throw new IllegalArgumentException("Contact already exists for email " + normalizedEmail);
        }
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setProject(project);
        contact.setOrganizationName(requiredValue(organizationName, "Organization"));
        contact.setContactName(blankToEmpty(contactName));
        contact.setEmail(normalizedEmail);
        contact.setNote(trimToNull(note));
        contact.setStatus(ContactStatus.NEW);
        Contact saved = contactRepository.save(contact);
        saveCustomFields(project, saved, customFields);
        return saved;
    }

    @Transactional
    public Contact updateEditableFields(
            UUID projectId,
            UUID contactId,
            String contactName,
            String email,
            ContactStatus status,
            String note
    ) {
        Contact contact = getContact(projectId, contactId);
        String normalizedEmail = requiredEmail(email);
        contactRepository.findByProjectIdAndEmail(projectId, normalizedEmail)
                .filter(existing -> !existing.getId().equals(contactId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Contact already exists for email " + normalizedEmail);
                });
        contact.setContactName(blankToEmpty(contactName));
        contact.setEmail(normalizedEmail);
        if (status != null) {
            contact.setStatus(status);
        }
        contact.setNote(trimToNull(note));
        return contactRepository.save(contact);
    }

    public long countByStatus(UUID projectId, ContactStatus status) {
        projectService.getProject(projectId);
        return contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, status);
    }

    public List<ContactCustomField> findCustomFields(UUID projectId, UUID contactId) {
        projectService.getProject(projectId);
        return contactCustomFieldRepository.findByProjectIdAndContactId(projectId, contactId);
    }

    @Transactional
    public void deleteContact(UUID projectId, UUID contactId) {
        Contact contact = getContact(projectId, contactId);
        boolean hasHistory = contact.getSentAt() != null
                || contact.getOutboundMessageId() != null
                || !contactMessageRepository.findByProjectIdAndContactIdOrderByMessageTimestampAsc(projectId, contactId).isEmpty();
        if (hasHistory) {
            contact.setDeletedAt(OffsetDateTime.now());
            contactRepository.save(contact);
        } else {
            contactRepository.delete(contact);
        }
    }

    private void saveCustomFields(Project project, Contact contact, Map<String, String> customFields) {
        if (customFields == null || customFields.isEmpty()) {
            return;
        }
        customFields.forEach((key, value) -> {
            String fieldValue = trimToNull(value);
            if (fieldValue == null) {
                return;
            }
            ContactCustomField field = new ContactCustomField();
            field.setId(UUID.randomUUID());
            field.setProject(project);
            field.setContact(contact);
            field.setFieldKey(key);
            field.setFieldValue(fieldValue);
            contactCustomFieldRepository.save(field);
        });
    }

    private String requiredEmail(String email) {
        String normalizedEmail = EmailUtils.normalize(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return normalizedEmail;
    }

    private String requiredValue(String value, String label) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return trimmed;
    }

    private String blankToEmpty(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
