package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.ContactMapper;
import com.pdasilem.contactwork.contact.ContactLookupService;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/contacts")
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

    @GetMapping
    public List<ContactResponse> getContacts(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String organization
    ) {
        return contactService.findContacts(status, email, organization)
                .stream()
                .map(contactMapper::toResponse)
                .toList();
    }

    @GetMapping("/{selector}")
    public ContactResponse getContact(@PathVariable @NotNull String selector) {
        return contactMapper.toResponse(contactLookupService.findBySelector(selector));
    }

    @PatchMapping("/{selector}/note")
    public ContactResponse updateNote(@PathVariable String selector, @RequestBody UpdateContactNoteRequest request) {
        var contact = contactLookupService.findBySelector(selector);
        contact.setNote(request.note());
        return contactMapper.toResponse(contactService.save(contact));
    }
}
