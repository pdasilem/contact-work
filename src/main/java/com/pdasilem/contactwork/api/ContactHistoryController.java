package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactLookupService;
import com.pdasilem.contactwork.history.ContactMessageService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
public class ContactHistoryController {

    private final ContactLookupService contactLookupService;
    private final ContactMessageService contactMessageService;
    private final ContactMessageMapper contactMessageMapper;

    public ContactHistoryController(
            ContactLookupService contactLookupService,
            ContactMessageService contactMessageService,
            ContactMessageMapper contactMessageMapper
    ) {
        this.contactLookupService = contactLookupService;
        this.contactMessageService = contactMessageService;
        this.contactMessageMapper = contactMessageMapper;
    }

    @GetMapping("/{selector}")
    public List<ContactMessageResponse> history(@PathVariable String selector) {
        Contact contact = contactLookupService.findBySelector(selector);
        return contactMessageService.findByContactId(contact.getId())
                .stream()
                .map(contactMessageMapper::toResponse)
                .toList();
    }
}
