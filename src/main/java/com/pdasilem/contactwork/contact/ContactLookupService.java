package com.pdasilem.contactwork.contact;

import com.pdasilem.contactwork.common.EmailUtils;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ContactLookupService {

    private final ContactRepository contactRepository;

    public ContactLookupService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    public Contact findBySelector(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Selector must not be blank");
        }
        if (selector.contains("@")) {
            String normalizedEmail = EmailUtils.normalize(selector);
            return contactRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found by email: " + selector));
        }
        final UUID contactId;
        try {
            contactId = UUID.fromString(selector);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Selector must be a contact UUID or an email address: " + selector);
        }
        return contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + selector));
    }
}
