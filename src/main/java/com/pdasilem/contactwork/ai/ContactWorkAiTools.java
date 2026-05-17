package com.pdasilem.contactwork.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ContactWorkAiTools {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

    private final ContactRepository contactRepository;
    private final MailboxMessageRepository mailboxMessageRepository;
    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ContactWorkAiTools(
            ContactRepository contactRepository,
            MailboxMessageRepository mailboxMessageRepository,
            AppProperties appProperties,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.contactRepository = contactRepository;
        this.mailboxMessageRepository = mailboxMessageRepository;
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Return sent, replied, bounced, failed, and new contact counts for a project.")
    public String projectStats(String projectId) {
        UUID id = UUID.fromString(projectId);
        return "new=" + count(id, ContactStatus.NEW)
                + ", sent=" + count(id, ContactStatus.SENT)
                + ", replied=" + count(id, ContactStatus.REPLIED)
                + ", bounced=" + count(id, ContactStatus.BOUNCED)
                + ", send_failed=" + count(id, ContactStatus.SEND_FAILED);
    }

    @Tool(description = "List contacts for a project, optionally filtered by status and search text.")
    public String contactsByStatus(String projectId, String status, String search) {
        UUID id = UUID.fromString(projectId);
        ContactStatus parsedStatus = parseStatus(status);
        return contactRepository.findByProjectIdAndDeletedAtIsNull(id).stream()
                .filter(contact -> parsedStatus == null || contact.getStatus() == parsedStatus)
                .filter(contact -> matches(contact, search))
                .limit(25)
                .map(contact -> contact.getStatus() + " | " + contact.getEmail() + " | "
                        + contact.getContactName() + " | " + contact.getOrganizationName())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No contacts.");
    }

    @Tool(description = "Return one contact's chronological mailbox conversation.")
    public String contactConversation(String projectId, String contactId) {
        return mailboxMessageRepository.findByProjectIdAndContactIdOrderByServiceDateAsc(
                        UUID.fromString(projectId),
                        UUID.fromString(contactId)
                ).stream()
                .map(this::formatMessage)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("No mailbox conversation.");
    }

    @Tool(description = "Return recent mailbox messages for a project.")
    public String recentMailboxMessages(String projectId) {
        return mailboxMessageRepository.findTop20ByProjectIdOrderByServiceDateDesc(UUID.fromString(projectId)).stream()
                .map(this::formatMessage)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("No mailbox messages.");
    }

    @Tool(description = "Search the public web with Brave for external/current facts only. Never include private project, contact, mailbox, credential, UUID, email address, message body, or ContactWork database text in the query.")
    public String braveWebSearch(String query) {
        if (query == null || query.isBlank()) {
            return "Brave Search query is required.";
        }
        String trimmedQuery = query.trim();
        if (containsSensitiveQueryData(trimmedQuery)) {
            return "Brave Search refused the query because it appears to contain private ContactWork data. Search only public external facts.";
        }
        AppProperties.Brave brave = appProperties.ai().brave();
        if (brave.apiKey() == null || brave.apiKey().isBlank()) {
            return "Brave Search is not configured. Set BRAVE_SEARCH_API_KEY.";
        }
        HttpRequest request = HttpRequest.newBuilder(searchUri(brave, trimmedQuery))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", brave.apiKey())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Brave Search failed with HTTP " + response.statusCode() + ".";
            }
            return compactSearchResults(response.body(), brave.count());
        } catch (IOException ex) {
            return "Brave Search failed: " + ex.getMessage();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "Brave Search was interrupted.";
        }
    }

    private long count(UUID projectId, ContactStatus status) {
        return contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, status);
    }

    private ContactStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(ContactStatus.values())
                .filter(value -> value.name().equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(Contact contact, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase();
        return contains(contact.getEmail(), needle)
                || contains(contact.getContactName(), needle)
                || contains(contact.getOrganizationName(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private boolean containsSensitiveQueryData(String query) {
        return query.length() > 240
                || EMAIL_PATTERN.matcher(query).find()
                || UUID_PATTERN.matcher(query).find();
    }

    private URI searchUri(AppProperties.Brave brave, String query) {
        String separator = brave.webSearchUrl().contains("?") ? "&" : "?";
        return URI.create(brave.webSearchUrl() + separator
                + "q=" + encode(query)
                + "&count=" + brave.count());
    }

    private String compactSearchResults(String body, int count) {
        try {
            JsonNode results = objectMapper.readTree(body).path("web").path("results");
            if (!results.isArray() || results.isEmpty()) {
                return "No Brave Search results.";
            }
            StringBuilder output = new StringBuilder();
            int emitted = 0;
            for (JsonNode result : results) {
                if (emitted >= count) {
                    break;
                }
                String title = trim(text(result, "title"), 120);
                String url = trim(text(result, "url"), 180);
                String snippet = trim(text(result, "description"), 240);
                if (!output.isEmpty()) {
                    output.append("\n\n");
                }
                output.append(title).append("\n").append(url).append("\n").append(snippet);
                emitted++;
            }
            return output.toString();
        } catch (IOException ex) {
            return "Brave Search returned unreadable results.";
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= maxLength ? compact : compact.substring(0, maxLength) + "...";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String formatMessage(MailboxMessage message) {
        return message.getServiceDate() + " | " + message.getDirection() + " | " + message.getSubject()
                + "\nfrom=" + message.getSenderEmail()
                + "\nto=" + message.getRecipientEmails()
                + "\nbody=" + trimBody(message.getBodyText());
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 600 ? body : body.substring(0, 600) + "...";
    }
}
