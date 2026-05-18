package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.ContactMapper;
import com.pdasilem.contactwork.contact.ContactLookupService;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ContactController {

    private final ContactService contactService;
    private final ContactLookupService contactLookupService;
    private final ContactMapper contactMapper;

    public ContactController(
            ContactService contactService,
            ContactLookupService contactLookupService,
            ContactMapper contactMapper
    ) {
        this.contactService = contactService;
        this.contactLookupService = contactLookupService;
        this.contactMapper = contactMapper;
    }

    @GetMapping(path = "/projects/{projectId}/contacts", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<?> getProjectContacts(
            @PathVariable UUID projectId,
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String organization,
            @RequestParam(required = false, defaultValue = "json") String format
    ) {
        List<ContactResponse> contacts = contactService.findContacts(projectId, status, email, organization)
                .stream()
                .map(contactMapper::toResponse)
                .toList();

        if ("table".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(formatAsTable(contacts));
        }

        return ResponseEntity.ok(contacts);
    }

    @GetMapping("/projects/{projectId}/contacts/{selector}")
    public ContactResponse getProjectContact(@PathVariable UUID projectId, @PathVariable @NotNull String selector) {
        return contactMapper.toResponse(contactLookupService.findBySelector(projectId, selector));
    }

    @PatchMapping("/projects/{projectId}/contacts/{selector}/note")
    public ContactResponse updateProjectNote(
            @PathVariable UUID projectId,
            @PathVariable String selector,
            @RequestBody UpdateContactNoteRequest request
    ) {
        var contact = contactLookupService.findBySelector(projectId, selector);
        contact.setNote(request.note());
        return contactMapper.toResponse(contactService.save(contact));
    }

    @DeleteMapping("/projects/{projectId}/contacts/{selector}")
    public ResponseEntity<Void> deleteProjectContact(@PathVariable UUID projectId, @PathVariable String selector) {
        var contact = contactLookupService.findBySelector(projectId, selector);
        contactService.deleteContact(projectId, contact.getId());
        return ResponseEntity.noContent().build();
    }

    private String formatAsTable(List<ContactResponse> contacts) {
        StringBuilder builder = new StringBuilder();
        String header = String.format(
                "%-36s  %-10s  %-24s  %-32s  %-24s  %-20s%n",
                "ID",
                "STATUS",
                "CONTACT",
                "EMAIL",
                "ORGANIZATION",
                "SENT AT"
        );
        builder.append(header);
        builder.repeat("-", header.length() - 1).append(System.lineSeparator());

        for (ContactResponse contact : contacts) {
            builder.append(String.format(
                    "%-36s  %-10s  %-24s  %-32s  %-24s  %-20s%n",
                    truncate(contact.id().toString(), 36),
                    truncate(contact.status().name(), 10),
                    truncate(contact.contactName(), 24),
                    truncate(contact.email(), 32),
                    truncate(contact.organizationName(), 24),
                    truncate(Objects.toString(contact.sentAt(), ""), 20)
            ));
        }

        if (contacts.isEmpty()) {
            builder.append("(no contacts)").append(System.lineSeparator());
        }

        return builder.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}