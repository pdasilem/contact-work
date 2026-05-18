package com.pdasilem.contactwork.contact;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.pdasilem.contactwork.api.ImportContactsResponse;
import com.pdasilem.contactwork.common.EmailUtils;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ContactImportService {
    private static final Logger log = LoggerFactory.getLogger(ContactImportService.class);

    private final ContactRepository contactRepository;
    private final ContactCustomFieldRepository contactCustomFieldRepository;
    private final ProjectService projectService;
    private final ProjectContactColumnService projectContactColumnService;
    private final CsvMapper csvMapper = CsvMapper.builder()
            .enable(CsvParser.Feature.WRAP_AS_ARRAY)
            .build();

    public ContactImportService(
            ContactRepository contactRepository,
            ContactCustomFieldRepository contactCustomFieldRepository,
            ProjectService projectService,
            ProjectContactColumnService projectContactColumnService
    ) {
        this.contactRepository = contactRepository;
        this.contactCustomFieldRepository = contactCustomFieldRepository;
        this.projectService = projectService;
        this.projectContactColumnService = projectContactColumnService;
    }

    @Transactional
    public ImportContactsResponse importContacts(UUID projectId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty");
        }
        try {
            return importContactsFromStream(projectId, file.getOriginalFilename(), file.getInputStream());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read CSV file", ex);
        }
    }

    @Transactional
    public ImportContactsResponse importContacts(UUID projectId, String filename, InputStream fileContent) {
        return importContactsFromStream(projectId, filename, fileContent);
    }

    private ImportContactsResponse importContactsFromStream(UUID projectId, String filename, InputStream fileContent) {
        Project project = projectService.getProject(projectId);
        log.info("CSV contact import started: projectId={}, filename={}", projectId, filename);

        int totalRows = 0;
        int inserted = 0;
        int skippedExisting = 0;
        int skippedInvalid = 0;

        try (InputStream inputStream = stripBom(fileContent)) {
            CsvSchema schema = CsvSchema.emptySchema();
            MappingIterator<String[]> iterator = csvMapper
                    .readerFor(String[].class)
                    .with(schema)
                    .readValues(inputStream);

            if (!iterator.hasNext()) {
                throw new IllegalArgumentException("CSV file does not contain a header row");
            }
            String[] header = iterator.next();
            validateHeader(header);
            Map<String, Integer> columns = indexColumns(project, header);
            Integer emailIndex = requiredColumn(columns, "email");
            Integer organizationIndex = requiredColumn(columns, "organization_name");

            while (iterator.hasNext()) {
                String[] row = iterator.next();
                totalRows++;

                String normalizedEmail = EmailUtils.normalize(valueAt(row, emailIndex));
                String organization = trimToEmpty(valueAt(row, organizationIndex));

                if (!EmailUtils.isValid(normalizedEmail) || organization.isBlank()) {
                    skippedInvalid++;
                    continue;
                }

                if (contactRepository.existsByProjectIdAndEmail(projectId, normalizedEmail)) {
                    skippedExisting++;
                    continue;
                }

                Contact contact = new Contact();
                contact.setId(UUID.randomUUID());
                contact.setProject(project);
                contact.setOrganizationName(organization);
                contact.setContactName(defaultContactName(valueAt(row, columns.get("contact_name"))));
                contact.setEmail(normalizedEmail);
                contact.setNote(trimToNull(valueAt(row, columns.get("note"))));
                contact.setStatus(ContactStatus.NEW);
                contactRepository.save(contact);
                saveCustomFields(project, contact, header, row);
                inserted++;
            }
        } catch (IOException ex) {
            log.warn("CSV contact import failed: projectId={}, filename={}", projectId, filename, ex);
            throw new IllegalArgumentException("Failed to parse CSV: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.warn("CSV contact import failed: projectId={}, filename={}", projectId, filename, ex);
            throw ex;
        }

        log.info("CSV contact import succeeded: projectId={}, filename={}, totalRows={}, inserted={}, skippedExisting={}, skippedInvalid={}",
                projectId, filename, totalRows, inserted, skippedExisting, skippedInvalid);
        return new ImportContactsResponse(totalRows, inserted, skippedExisting, skippedInvalid);
    }

    private void validateHeader(String[] header) {
        if (header.length == 0) {
            throw new IllegalArgumentException("CSV header is empty");
        }
    }

    private Map<String, Integer> indexColumns(Project project, String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.length; index++) {
            String label = trimToEmpty(header[index]);
            String key = standardKey(label);
            ContactColumnSource source = isStandard(key) ? ContactColumnSource.STANDARD : ContactColumnSource.CUSTOM;
            projectContactColumnService.ensureColumn(project, key, label, source, index);
            columns.putIfAbsent(key, index);
        }
        return columns;
    }

    private Integer requiredColumn(Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null) {
            throw new IllegalArgumentException("CSV header must contain " + key);
        }
        return index;
    }

    private void saveCustomFields(Project project, Contact contact, String[] header, String[] row) {
        for (int index = 0; index < header.length; index++) {
            String key = standardKey(header[index]);
            if (isStandard(key)) {
                continue;
            }
            ContactCustomField field = new ContactCustomField();
            field.setId(UUID.randomUUID());
            field.setProject(project);
            field.setContact(contact);
            field.setFieldKey(key);
            field.setFieldValue(trimToNull(valueAt(row, index)));
            contactCustomFieldRepository.save(field);
        }
    }

    private String defaultContactName(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    private String valueAt(String[] row, Integer index) {
        if (index == null || index < 0 || index >= row.length) {
            return null;
        }
        return row[index];
    }

    private boolean isStandard(String key) {
        return "email".equals(key)
                || "organization_name".equals(key)
                || "contact_name".equals(key)
                || "note".equals(key);
    }

    private String standardKey(String header) {
        String normalized = trimToEmpty(header).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return switch (normalized) {
            case "organization", "org", "company", "organization_name" -> "organization_name";
            case "contact", "contact_name", "name", "person" -> "contact_name";
            case "note", "notes" -> "note";
            default -> normalized;
        };
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