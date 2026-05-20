package com.pdasilem.contactwork.template;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.mail.MailTemplateRenderer;
import com.pdasilem.contactwork.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {
    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private static final String SUBJECT_PREFIX = "Subject";
    private static final int SUBJECT_SPACING_BEFORE_TWIPS = 240;

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;
    private final PdfConversionService pdfConversionService;
    private final MailTemplateRenderer mailTemplateRenderer;

    public TemplateService(
            AppProperties appProperties,
            ResourceLoader resourceLoader,
            PdfConversionService pdfConversionService,
            MailTemplateRenderer mailTemplateRenderer
    ) {
        this.appProperties = appProperties;
        this.resourceLoader = resourceLoader;
        this.pdfConversionService = pdfConversionService;
        this.mailTemplateRenderer = mailTemplateRenderer;
    }

    public GeneratedLetter generateLetterPdf(Project project, Contact contact) {
        return generateLetterPdf(project, resourceLoader.getResource(project.getLetterTemplate()), contact);
    }

    public GeneratedLetter generateLetterPdf(Project project, Resource templateResource, Contact contact) {
        try {
            Path workingDir = Files.createDirectories(Path.of(appProperties.resources().workingDir()));
            Path jobDir = Files.createTempDirectory(workingDir, "letter-");
            Path docxPath = jobDir.resolve("letter.docx");
            try (InputStream inputStream = templateResource.getInputStream();
                 XWPFDocument document = new XWPFDocument(inputStream)) {
                replaceInDocument(document, project, contact);
                applyLayoutTweaks(document);
                applySmartQuotes(document);
                document.write(Files.newOutputStream(docxPath));
            }

            Path pdfPath = pdfConversionService.convertToPdf(docxPath);
            return new GeneratedLetter(docxPath, pdfPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate letter PDF", ex);
        }
    }

    private void replaceInDocument(XWPFDocument document, Project project, Contact contact) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceInParagraph(paragraph, project, contact);
        }
        for (var table : document.getTables()) {
            table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell ->
                            cell.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, project, contact))));
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Project project, Contact contact) {
        String fullText = paragraph.getRuns().stream()
                .map(run -> run.getText(0))
                .filter(text -> text != null)
                .reduce("", String::concat);
        if (!fullText.contains("{{")) {
            return;
        }
        String replaced = mailTemplateRenderer.render(fullText, project, contact);
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
        if (runs.isEmpty()) {
            paragraph.createRun().setText(replaced, 0);
        } else {
            runs.getFirst().setText(replaced, 0);
        }
    }

    private void applyLayoutTweaks(XWPFDocument document) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText() != null && paragraph.getText().startsWith(SUBJECT_PREFIX)) {
                paragraph.setSpacingBefore(SUBJECT_SPACING_BEFORE_TWIPS);
                return;
            }
        }
    }

    private void applySmartQuotes(XWPFDocument document) {
        QuoteState quoteState = new QuoteState();
        applySmartQuotes(document.getParagraphs(), quoteState);
        for (var table : document.getTables()) {
            table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell ->
                            applySmartQuotes(cell.getParagraphs(), quoteState)));
        }
    }

    private void applySmartQuotes(List<XWPFParagraph> paragraphs, QuoteState quoteState) {
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                String text = run.getText(0);
                if (text == null || text.indexOf('"') < 0) {
                    continue;
                }
                run.setText(replaceStraightQuotes(text, quoteState), 0);
            }
        }
    }

    private String replaceStraightQuotes(String text, QuoteState quoteState) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '"') {
                builder.append(quoteState.open ? '“' : '”');
                quoteState.open = !quoteState.open;
                continue;
            }
            builder.append(character);
        }
        return builder.toString();
    }

    private static final class QuoteState {
        private boolean open = true;
    }
}
