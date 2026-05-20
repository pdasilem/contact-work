package com.pdasilem.contactwork.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactCustomFieldRepository;
import com.pdasilem.contactwork.contact.ProjectContactColumnRepository;
import com.pdasilem.contactwork.mail.MailTemplateRenderer;
import com.pdasilem.contactwork.project.Project;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class TemplateServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReplaceContactNamePlaceholderInDocx() throws Exception {
        PdfConversionService pdfConversionService = mock(PdfConversionService.class);
        Path fakePdf = Files.createFile(tempDir.resolve("letter.pdf"));
        when(pdfConversionService.convertToPdf(org.mockito.ArgumentMatchers.any())).thenReturn(fakePdf);

        AppProperties properties = new AppProperties(
                new AppProperties.Resources(tempDir.toString()),
                new AppProperties.Mail(0, "0 */5 * * * *", null, null),
                null
        );

        ContactCustomFieldRepository customFieldRepo = mock(ContactCustomFieldRepository.class);
        ProjectContactColumnRepository columnRepo = mock(ProjectContactColumnRepository.class);
        when(customFieldRepo.findByProjectIdAndContactId(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(columnRepo.findByProjectIdOrderByColumnOrderAsc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        MailTemplateRenderer mailTemplateRenderer = new MailTemplateRenderer(customFieldRepo, columnRepo);

        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setLetterTemplate("classpath:data/Letter.docx");

        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        contact.setContactName("Alice Example");
        contact.setOrganizationName("Test Org");
        contact.setEmail("alice@example.com");

        TemplateService service = new TemplateService(properties, new DefaultResourceLoader(), pdfConversionService, mailTemplateRenderer);
        GeneratedLetter generatedLetter = service.generateLetterPdf(project, contact);

        try (InputStream inputStream = Files.newInputStream(generatedLetter.docxPath());
             XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("Alice Example");
            assertThat(text).doesNotContain("{{contact_name}}");
            assertThat(text).contains("“Nano-cartilage”");
        }
    }
}
