package com.pdasilem.contactwork.ai;

import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class MessageVectorIndexer {
    private static final Logger log = LoggerFactory.getLogger(MessageVectorIndexer.class);

    private final VectorStore vectorStore;
    private final MailboxMessageRepository mailboxMessageRepository;

    public MessageVectorIndexer(VectorStore vectorStore, MailboxMessageRepository mailboxMessageRepository) {
        this.vectorStore = vectorStore;
        this.mailboxMessageRepository = mailboxMessageRepository;
    }

    public void index(MailboxMessage message) {
        Document doc = toDocument(message);
        vectorStore.add(List.of(doc));
    }

    public int indexProject(UUID projectId) {
        List<MailboxMessage> messages = mailboxMessageRepository.findByProjectIdOrderByServiceDateAsc(projectId);
        if (messages.isEmpty()) {
            return 0;
        }
        List<Document> docs = messages.stream()
                .map(this::toDocument)
                .toList();
        vectorStore.add(docs);
        log.info("Indexed {} messages for project {}", docs.size(), projectId);
        return docs.size();
    }

    private Document toDocument(MailboxMessage message) {
        if (message.getProject() == null || message.getContact() == null) {
            throw new IllegalArgumentException("MailboxMessage must have project and contact set");
        }
        String content = formatContent(message);
        Map<String, Object> metadata = Map.of(
                "projectId", message.getProject().getId().toString(),
                "contactId", message.getContact().getId().toString(),
                "contactEmail", safe(message.getContact().getEmail()),
                "direction", message.getDirection() != null ? message.getDirection().name() : "UNKNOWN",
                "subject", safe(message.getSubject()),
                "serviceDate", message.getServiceDate() != null ? message.getServiceDate().toString() : ""
        );
        Document doc = new Document(message.getId().toString(), content, metadata);
        return doc;
    }

    private String formatContent(MailboxMessage message) {
        StringBuilder sb = new StringBuilder();
        if (message.getServiceDate() != null) {
            sb.append("Date: ").append(message.getServiceDate()).append("\n");
        }
        if (message.getDirection() != null) {
            sb.append("Direction: ").append(message.getDirection()).append("\n");
        }
        sb.append("From: ").append(safe(message.getSenderEmail())).append("\n");
        if (message.getContact() != null) {
            sb.append("Contact: ").append(safe(message.getContact().getContactName()))
                    .append(" (").append(safe(message.getContact().getEmail())).append(")\n");
        }
        sb.append("Subject: ").append(safe(message.getSubject())).append("\n");
        sb.append("Body: ").append(safe(message.getBodyText()));
        return sb.toString();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
