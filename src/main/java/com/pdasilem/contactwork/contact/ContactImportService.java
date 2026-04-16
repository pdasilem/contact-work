package com.pdasilem.contactwork.contact;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.common.EmailUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContactImportService {
    private static final Logger log = LoggerFactory.getLogger(ContactImportService.class);
    private static final int MIN_COLUMNS = 6;
    private static final int ORGANIZATION_INDEX = 1;
    private static final int COUNTRY_INDEX = 2;
    private static final int CONTACT_NAME_INDEX = 3;
    private static final int EMAIL_INDEX = 4;
    private static final int NOTES_INDEX = 5;

    private final ContactRepository contactRepository;
    private final CsvMapper csvMapper = CsvMapper.builder()
            .enable(CsvParser.Feature.WRAP_AS_ARRAY)
            .build();

    public ContactImportService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional
    public ImportContactsResponse importContacts(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }

        int totalRows = 0;
        int inserted = 0;
        int skippedExisting = 0;
        int skippedInvalid = 0;

        try (InputStream inputStream = stripBom(file.getInputStream())) {
            CsvSchema schema = CsvSchema.emptySchema();
            MappingIterator<String[]> iterator = csvMapper
                    .readerFor(String[].class)
                    .with(schema)
                    .readValues(inputStream);

            if (!iterator.hasNext()) {
                throw new IllegalArgumentException("CSV file does not contain a header row");
            }
            validateHeader(iterator.next());

            while (iterator.hasNext()) {
                String[] row = iterator.next();
                totalRows++;

                if (row.length < MIN_COLUMNS) {
                    skippedInvalid++;
                    continue;
                }

                String normalizedEmail = EmailUtils.normalize(row[EMAIL_INDEX]);

                if (!EmailUtils.isValid(normalizedEmail)) {
                    skippedInvalid++;
                    continue;
                }

                if (contactRepository.existsByEmail(normalizedEmail)) {
                    skippedExisting++;
                    continue;
                }

                Contact contact = new Contact();
                contact.setId(UUID.randomUUID());
                contact.setOrganizationName(trimToEmpty(row[ORGANIZATION_INDEX]));
                contact.setCountry(trimToNull(row[COUNTRY_INDEX]));
                contact.setContactName(trimToEmpty(row[CONTACT_NAME_INDEX]));
                contact.setEmail(normalizedEmail);
                contact.setPreclinicalNotes(trimToNull(row[NOTES_INDEX]));
                contact.setStatus(ContactStatus.NEW);
                contactRepository.save(contact);
                inserted++;
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to parse CSV: " + ex.getMessage(), ex);
        }

        log.info("Imported contacts from {}: totalRows={}, inserted={}, skippedExisting={}, skippedInvalid={}",
                file.getOriginalFilename(), totalRows, inserted, skippedExisting, skippedInvalid);
        return new ImportContactsResponse(totalRows, inserted, skippedExisting, skippedInvalid);
    }

    private void validateHeader(String[] header) {
        if (header.length < MIN_COLUMNS) {
            throw new IllegalArgumentException("CSV header must contain at least " + MIN_COLUMNS + " columns");
        }
    }

    private InputStream stripBom(InputStream inputStream) throws IOException {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, 3);
        byte[] bom = new byte[3];
        int read = pushbackInputStream.read(bom, 0, bom.length);
        if (read == 3 && (bom[0] & 0xFF) == 0xEF && (bom[1] & 0xFF) == 0xBB && (bom[2] & 0xFF) == 0xBF) {
            return pushbackInputStream;
        }
        if (read > 0) {
            pushbackInputStream.unread(bom, 0, read);
        }
        return pushbackInputStream;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
