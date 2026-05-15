package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.ContactImportService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ContactImportController {

    private final ContactImportService contactImportService;

    public ContactImportController(ContactImportService contactImportService) {
        this.contactImportService = contactImportService;
    }

    @PostMapping(path = "/projects/{projectId}/contacts/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportContactsResponse importProjectContacts(@PathVariable UUID projectId, @RequestPart("file") MultipartFile file) {
        return contactImportService.importContacts(projectId, file);
    }
}
