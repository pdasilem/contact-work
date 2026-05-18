package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactColumnSource;
import com.pdasilem.contactwork.contact.ContactCustomField;
import com.pdasilem.contactwork.contact.ContactCustomFieldRepository;
import com.pdasilem.contactwork.contact.ProjectContactColumn;
import com.pdasilem.contactwork.contact.ProjectContactColumnRepository;
import com.pdasilem.contactwork.project.Project;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MailTemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");

    private final ContactCustomFieldRepository contactCustomFieldRepository;
    private final ProjectContactColumnRepository projectContactColumnRepository;

    public MailTemplateRenderer(
            ContactCustomFieldRepository contactCustomFieldRepository,
            ProjectContactColumnRepository projectContactColumnRepository
    ) {
        this.contactCustomFieldRepository = contactCustomFieldRepository;
        this.projectContactColumnRepository = projectContactColumnRepository;
    }

    public String render(String template, Project project, Contact contact) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Map<String, Supplier<String>> values = valuesByPlaceholder(project, contact);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            Supplier<String> value = values.get(normalize(placeholder));
            if (value == null) {
                throw new IllegalArgumentException("Unknown email template placeholder: {" + placeholder + "}");
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(blankIfNull(value.get())));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private Map<String, Supplier<String>> valuesByPlaceholder(Project project, Contact contact) {
        Map<String, Supplier<String>> values = new HashMap<>();
        putStandard(values, "Contact", contact::getContactName);
        putStandard(values, "Contact name", contact::getContactName);
        putStandard(values, "contact_name", contact::getContactName);
        putStandard(values, "Email", contact::getEmail);
        putStandard(values, "Organization", contact::getOrganizationName);
        putStandard(values, "Organization name", contact::getOrganizationName);
        putStandard(values, "organization_name", contact::getOrganizationName);
        putStandard(values, "Note", contact::getNote);

        UUID projectId = project.getId();
        UUID contactId = contact.getId();
        Map<String, String> customValuesByKey = new HashMap<>();
        for (ContactCustomField field : contactCustomFieldRepository.findByProjectIdAndContactId(projectId, contactId)) {
            customValuesByKey.put(normalize(field.getFieldKey()), field.getFieldValue());
        }
        for (ProjectContactColumn column : projectContactColumnRepository.findByProjectIdOrderByColumnOrderAsc(projectId)) {
            if (!column.isVisible() || column.getSourceType() != ContactColumnSource.CUSTOM) {
                continue;
            }
            String fieldKey = column.getColumnKey();
            String displayLabel = column.getDisplayLabel();
            if (fieldKey == null || displayLabel == null || displayLabel.isBlank()) {
                continue;
            }
            values.put(normalize(displayLabel), () -> customValuesByKey.get(normalize(fieldKey)));
        }
        return values;
    }

    private void putStandard(Map<String, Supplier<String>> values, String placeholder, Supplier<String> value) {
        values.put(normalize(placeholder), value);
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
