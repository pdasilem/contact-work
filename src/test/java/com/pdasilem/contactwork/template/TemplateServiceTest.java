package com.pdasilem.contactwork.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pdasilem.contactwork.config.AppProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
                new AppProperties.Resources(
                        "classpath:data/Letter.docx",
                        "classpath:data/Pitch_deck_en.pdf",
                        tempDir.toString()
                ),
                new AppProperties.Mail(
                        "Test Subject",
                        "Test body",
                        "letter.pdf",
                        "Pitch_deck_en.pdf",
                        "sender@example.com",
                        0,
                        "0 */5 * * * *",
                        new AppProperties.Gmail("user@example.com", "app-password")
                )
        );

        TemplateService service = new TemplateService(properties, new DefaultResourceLoader(), pdfConversionService);
        GeneratedLetter generatedLetter = service.generateLetterPdf("Alice Example");

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
