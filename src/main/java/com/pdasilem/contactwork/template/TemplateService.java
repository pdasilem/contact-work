package com.pdasilem.contactwork.template;

import com.pdasilem.contactwork.config.AppProperties;
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
    private static final String CONTACT_NAME_PLACEHOLDER = "{{contact_name}}";
    private static final String SUBJECT_PREFIX = "Subject";
    private static final int SUBJECT_SPACING_BEFORE_TWIPS = 240;

    private final AppProperties appProperties;
    private final ResourceLoader resourceLoader;
    private final PdfConversionService pdfConversionService;

    public TemplateService(AppProperties appProperties, ResourceLoader resourceLoader, PdfConversionService pdfConversionService) {
        this.appProperties = appProperties;
        this.resourceLoader = resourceLoader;
        this.pdfConversionService = pdfConversionService;
    }

    public GeneratedLetter generateLetterPdf(String contactName) {
        try {
            Path workingDir = Files.createDirectories(Path.of(appProperties.resources().workingDir()));
            Path jobDir = Files.createTempDirectory(workingDir, "letter-");
            Path docxPath = jobDir.resolve("letter.docx");
            Resource templateResource = resourceLoader.getResource(appProperties.resources().letterTemplate());
            try (InputStream inputStream = templateResource.getInputStream();
                 XWPFDocument document = new XWPFDocument(inputStream)) {
                replaceInDocument(document, contactName);
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

    public Resource getPitchDeckResource() {
        return resourceLoader.getResource(appProperties.resources().pitchDeck());
    }

    private void replaceInDocument(XWPFDocument document, String contactName) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceInParagraph(paragraph, contactName);
        }
        for (var table : document.getTables()) {
            table.getRows().forEach(row ->
                    row.getTableCells().forEach(cell ->
                            cell.getParagraphs().forEach(paragraph -> replaceInParagraph(paragraph, contactName))));
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, String contactName) {
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text == null || !text.contains(CONTACT_NAME_PLACEHOLDER)) {
                continue;
            }
            run.setText(text.replace(CONTACT_NAME_PLACEHOLDER, contactName), 0);
            log.debug("Applied contact name placeholder for {}", contactName);
            return;
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
