package com.pdasilem.contactwork.api;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactLookupService;
import com.pdasilem.contactwork.template.GeneratedLetter;
import com.pdasilem.contactwork.template.TemplateService;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/letters")
public class LetterPreviewController {

    private final ContactLookupService contactLookupService;
    private final TemplateService templateService;

    public LetterPreviewController(ContactLookupService contactLookupService, TemplateService templateService) {
        this.contactLookupService = contactLookupService;
        this.templateService = templateService;
    }

    @GetMapping("/{selector}/pdf")
    public ResponseEntity<byte[]> generatePdf(@PathVariable String selector) throws IOException {
        Contact contact = contactLookupService.findBySelector(selector);
        GeneratedLetter generatedLetter = templateService.generateLetterPdf(contact.getContactName());
        try {
            byte[] pdfBytes = Files.readAllBytes(generatedLetter.pdfPath());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename("letter-" + contact.getId() + ".pdf")
                            .build().toString())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } finally {
            Files.deleteIfExists(generatedLetter.docxPath());
            Files.deleteIfExists(generatedLetter.pdfPath());
            Files.deleteIfExists(generatedLetter.docxPath().getParent());
        }
    }
}
