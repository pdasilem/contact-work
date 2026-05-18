package com.pdasilem.contactwork.contact;

import com.pdasilem.contactwork.common.EmailUtils;
import com.pdasilem.contactwork.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ContactLookupService {

    private final ContactRepository contactRepository;
    private final ProjectService projectService;

    public ContactLookupService(ContactRepository contactRepository, ProjectService projectService) {
        this.contactRepository = contactRepository;
        this.projectService = projectService;
    }

    public Contact findBySelector(UUID projectId, String selector) {
        projectService.getProject(projectId);
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector must not be blank");
        }
        if (selector.contains("@")) {
            String normalizedEmail = EmailUtils.normalize(selector);
            return contactRepository.findByProjectIdAndEmail(projectId, normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found by email in project " + projectId + ": " + selector));
        }
        final UUID contactId;
        try {
            contactId = UUID.fromString(selector);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Selector must be a contact UUID or an email address: " + selector);
        }
        return contactRepository.findByProjectIdAndId(projectId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + selector));
    }
}
