package com.pdasilem.contactwork.ai;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactService;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.conversation.ContactConversationSummary;
import com.pdasilem.contactwork.conversation.ContactConversationSummaryRepository;
import com.pdasilem.contactwork.conversation.MailboxMessage;
import com.pdasilem.contactwork.conversation.MailboxMessageRepository;
import com.pdasilem.contactwork.project.AiProvider;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalAiService {

    private static final int MAX_HISTORY_CHARS = 6_000;
    private static final int MAX_CONTACT_CONTEXT_CHARS = 12_000;
    private static final int MAX_SESSION_TITLE_LENGTH = 120;
    private static final DateTimeFormatter SESSION_TITLE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectProvider<OllamaChatModel> ollamaChatModel;
    private final ObjectProvider<GoogleGenAiChatModel> googleChatModel;
    private final OllamaModelLifecycleService ollamaModelLifecycleService;
    private final AppAiSettingsService appAiSettingsService;
    private final ProjectService projectService;
    private final ContactService contactService;
    private final MailboxMessageRepository mailboxMessageRepository;
    private final ContactConversationSummaryRepository summaryRepository;
    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;

    @Autowired
    public LocalAiService(
            ObjectProvider<OllamaChatModel> ollamaChatModel,
            ObjectProvider<GoogleGenAiChatModel> googleChatModel,
            OllamaModelLifecycleService ollamaModelLifecycleService,
            AppAiSettingsService appAiSettingsService,
            ProjectService projectService,
            ContactService contactService,
            MailboxMessageRepository mailboxMessageRepository,
            ContactConversationSummaryRepository summaryRepository,
            AiChatSessionRepository sessionRepository,
            AiChatMessageRepository messageRepository
    ) {
        this.ollamaChatModel = ollamaChatModel;
        this.googleChatModel = googleChatModel;
        this.ollamaModelLifecycleService = ollamaModelLifecycleService;
        this.appAiSettingsService = appAiSettingsService;
        this.projectService = projectService;
        this.contactService = contactService;
        this.mailboxMessageRepository = mailboxMessageRepository;
        this.summaryRepository = summaryRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public Optional<ContactConversationSummary> findSummary(UUID contactId) {
        return summaryRepository.findByContactId(contactId);
    }

    public List<AiChatMessage> projectMessages(UUID projectId) {
        return sessionRepository.findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.PROJECT, projectId)
                .map(session -> messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()))
                .orElse(List.of());
    }

    public List<AiChatMessage> projectMessages(UUID projectId, UUID sessionId) {
        return messages(projectSession(projectId, sessionId));
    }

    public List<AiChatMessage> contactMessages(UUID contactId) {
        return sessionRepository.findFirstByScopeAndContactIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.CONTACT, contactId)
                .map(session -> messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()))
                .orElse(List.of());
    }

    public List<AiChatMessage> contactMessages(UUID projectId, UUID contactId, UUID sessionId) {
        return messages(contactSession(projectId, contactId, sessionId));
    }

    public List<AiChatSession> projectSessions(UUID projectId) {
        return sessionRepository.findByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.PROJECT, projectId);
    }

    public List<AiChatSession> contactSessions(UUID contactId) {
        return sessionRepository.findByScopeAndContactIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.CONTACT, contactId);
    }

    @Transactional
    public ContactConversationSummary summarizeContact(UUID projectId, UUID contactId) {
        Project project = projectService.getProject(projectId);
        Contact contact = contactService.getContact(projectId, contactId);
        String conversation = contactConversation(projectId, contactId);
        AppAiSettings settings = appAiSettingsService.current();
        String summary = callModel(
                project,
                projectService.aiSystemPrompt(project) + "\n\n"
                        + "Task: summarize this one contact mailbox conversation only. Return concise bullets. Do not change statuses.",
                "Contact: " + contact.getContactName() + " <" + contact.getEmail() + ">\n\n" + conversation
        );

        ContactConversationSummary entity = summaryRepository.findByContactId(contactId)
                .orElseGet(ContactConversationSummary::new);
        entity.setContact(contact);
        entity.setSummaryText(summary == null ? "" : summary.trim());
        entity.setProvider(settings.getProvider());
        entity.setModel(settings.getModel());
        entity.setGeneratedAt(OffsetDateTime.now());
        return summaryRepository.save(entity);
    }

    @Transactional
    public AiChatMessage askProject(UUID projectId, String question) {
        Project project = projectService.getProject(projectId);
        AiChatSession session = sessionRepository.findFirstByScopeAndProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.PROJECT, projectId)
                .orElseGet(() -> newProjectSession(project));
        return ask(session, projectService.aiSystemPrompt(project) + "\n\n"
                        + "Project AI scope: answer about this one project. Use only the provided ContactWork context.",
                projectContext(project), question, project);
    }

    @Transactional
    public AiChatMessage askProject(UUID projectId, UUID sessionId, String question) {
        Project project = projectService.getProject(projectId);
        AiChatSession session = sessionId == null ? newProjectSession(project) : projectSession(projectId, sessionId);
        return ask(session, projectService.aiSystemPrompt(project) + "\n\n"
                        + "Project AI scope: answer about this one project. Use only the provided ContactWork context.",
                projectContext(project), question, project);
    }

    @Transactional
    public AiChatMessage askContact(UUID projectId, UUID contactId, String question) {
        Project project = projectService.getProject(projectId);
        Contact contact = contactService.getContact(projectId, contactId);
        AiChatSession session = sessionRepository.findFirstByScopeAndContactIdAndArchivedAtIsNullOrderByUpdatedAtDesc(AiChatScope.CONTACT, contactId)
                .orElseGet(() -> newContactSession(contact));
        return ask(session, projectService.aiSystemPrompt(project) + "\n\n"
                        + "Contact AI scope: answer about one contact only. Use only this contact conversation. Never mutate statuses.",
                "projectId=" + projectId + "\ncontactId=" + contactId + "\n" + contactConversation(projectId, contactId),
                question, project);
    }

    @Transactional
    public AiChatMessage askContact(UUID projectId, UUID contactId, UUID sessionId, String question) {
        Project project = projectService.getProject(projectId);
        Contact contact = contactService.getContact(projectId, contactId);
        AiChatSession session = sessionId == null ? newContactSession(contact) : contactSession(projectId, contactId, sessionId);
        return ask(session, projectService.aiSystemPrompt(project) + "\n\n"
                        + "Contact AI scope: answer about one contact only. Use only this contact conversation. Never mutate statuses.",
                "projectId=" + projectId + "\ncontactId=" + contactId + "\n" + contactConversation(projectId, contactId),
                question, project);
    }

    @Transactional
    public AiChatSession newProjectChat(UUID projectId) {
        return sessionRepository.save(newProjectSession(projectService.getProject(projectId)));
    }

    @Transactional
    public AiChatSession newContactChat(UUID projectId, UUID contactId) {
        return sessionRepository.save(newContactSession(contactService.getContact(projectId, contactId)));
    }

    @Transactional
    public void archiveSession(UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI chat session not found: " + sessionId));
        session.setArchivedAt(OffsetDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    @Transactional
    public AiChatSession renameSession(UUID sessionId, String title) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI chat session not found: " + sessionId));
        session.setTitle(validateSessionTitle(title));
        return sessionRepository.save(session);
    }

    @Transactional
    public AiChatSession compactSession(UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI chat session not found: " + sessionId));
        List<AiChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        if (messages.isEmpty()) {
            return session;
        }
        String summary = callModel(session.getProject(),
                "Summarize this chat for future context. Preserve decisions, facts, user goals, and unresolved questions.",
                formatHistory(messages));
        session.setSummary(summary == null ? "" : summary.trim());
        return sessionRepository.save(session);
    }

    private AiChatMessage ask(AiChatSession session, String system, String context, String question, Project project) {
        AppAiSettings settings = appAiSettingsService.current();
        AiProvider provider = settings.getProvider();
        String model = settings.getModel();
        session.setProvider(provider);
        session.setModel(model);
        AiChatSession savedSession = sessionRepository.save(session);
        saveMessage(savedSession, AiChatRole.USER, question, null);
        String history = history(savedSession);
        String answer = callModel(project, system,
                "Context:\n" + context + "\n\nPrior chat:\n" + history + "\n\nQuestion:\n" + question);
        AiChatMessage assistantMessage = saveMessage(savedSession, AiChatRole.ASSISTANT, answer == null ? "" : answer.trim(), provider, model);
        savedSession.setTitle(question.length() > 80 ? question.substring(0, 80) : question);
        sessionRepository.save(savedSession);
        return assistantMessage;
    }

    private AiChatSession newProjectSession(Project project) {
        AiChatSession session = new AiChatSession();
        session.setScope(AiChatScope.PROJECT);
        session.setProject(project);
        session.setTitle(defaultSessionTitle());
        AppAiSettings settings = appAiSettingsService.current();
        session.setProvider(settings.getProvider());
        session.setModel(settings.getModel());
        return session;
    }

    private AiChatSession newContactSession(Contact contact) {
        AiChatSession session = new AiChatSession();
        session.setScope(AiChatScope.CONTACT);
        session.setProject(contact.getProject());
        session.setContact(contact);
        session.setTitle(defaultSessionTitle());
        AppAiSettings settings = appAiSettingsService.current();
        session.setProvider(settings.getProvider());
        session.setModel(settings.getModel());
        return session;
    }

    private AiChatMessage saveMessage(AiChatSession session, AiChatRole role, String content, String messageModel) {
        return saveMessage(session, role, content, null, messageModel);
    }

    private AiChatMessage saveMessage(AiChatSession session, AiChatRole role, String content, AiProvider provider, String messageModel) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setRole(role);
        message.setContent(content);
        message.setProvider(provider);
        message.setModel(messageModel);
        return messageRepository.save(message);
    }

    private String history(AiChatSession session) {
        List<String> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .toList();
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            messages = new ArrayList<>(messages);
            messages.add(0, "SUMMARY: " + session.getSummary().trim());
        }
        return boundedNewest(messages, MAX_HISTORY_CHARS, "[Older chat messages omitted due to context limit]");
    }

    private String formatHistory(List<AiChatMessage> messages) {
        return messages.stream()
                .map(message -> message.getRole() + ": " + message.getContent())
                .reduce("", (left, right) -> left.isBlank() ? right : left + "\n\n" + right);
    }

    private String contactConversation(UUID projectId, UUID contactId) {
        List<String> messages = mailboxMessageRepository.findByProjectIdAndContactIdOrderByServiceDateAsc(projectId, contactId).stream()
                .map(this::formatMessage)
                .toList();
        String conversation = boundedNewest(messages, MAX_CONTACT_CONTEXT_CHARS,
                "[Older mailbox messages omitted due to context limit]");
        return conversation.isBlank() ? "No mailbox conversation." : conversation;
    }

    private String boundedNewest(List<String> items, int maxChars, String omissionNote) {
        if (items.isEmpty()) {
            return "";
        }
        List<String> selected = new ArrayList<>();
        int chars = 0;
        boolean omitted = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            String item = trimToBudget(items.get(i), maxChars);
            int separatorChars = selected.isEmpty() ? 0 : 2;
            if (!selected.isEmpty() && chars + separatorChars + item.length() > maxChars) {
                omitted = true;
                break;
            }
            selected.add(item);
            chars += separatorChars + item.length();
        }
        Collections.reverse(selected);
        if (omitted) {
            selected.add(0, omissionNote);
        }
        return String.join("\n\n", selected);
    }

    private String trimToBudget(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(text.length() - maxChars);
    }

    private String projectContext(Project project) {
        StringBuilder context = new StringBuilder();
        context.append("projectId=").append(project.getId())
                .append("\nname=").append(project.getName())
                .append("\ndescription=").append(project.getDescription())
                .append("\nsender=").append(project.getMailFrom())
                .append("\nlastMailSyncAt=").append(project.getLastMailSyncAt())
                .append("\ncontactStatusCounts:");
        for (ContactStatus status : ContactStatus.values()) {
            context.append("\n").append(status).append("=")
                    .append(contactService.countByStatus(project.getId(), status));
        }
        return context.toString();
    }

    private String callModel(Project project, String system, String user) {
        AppAiSettings settings = appAiSettingsService.current();
        AiProvider provider = settings.getProvider();
        String model = settings.getModel();
        double temperature = settings.getTemperature();
        ChatModel modelClient = chatModel(provider);
        Prompt prompt = switch (provider) {
            case LOCAL_OLLAMA -> new Prompt(
                    List.of(new SystemMessage(system), new UserMessage(user)),
                    OllamaChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .build()
            );
            case GOOGLE_GENAI -> {
                if (ollamaModelLifecycleService != null) {
                    ollamaModelLifecycleService.unload(appAiSettingsService.defaultLocalAiModel());
                }
                yield new Prompt(
                        List.of(new SystemMessage(system), new UserMessage(user)),
                        GoogleGenAiChatOptions.builder()
                                .model(model)
                                .temperature(temperature)
                                .build()
                );
            }
        };
        ChatResponse response = modelClient.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private ChatModel chatModel(AiProvider provider) {
        ChatModel model = provider == AiProvider.GOOGLE_GENAI
                ? googleChatModel.getIfAvailable()
                : ollamaChatModel.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("AI provider is not configured: " + provider);
        }
        return model;
    }

    private String formatMessage(MailboxMessage message) {
        return message.getServiceDate() + " | " + message.getDirection() + " | " + message.getSubject()
                + "\nfrom=" + message.getSenderEmail()
                + "\nto=" + message.getRecipientEmails()
                + "\ncc=" + message.getCcEmails()
                + "\nbody=" + (message.getBodyText() == null ? "" : message.getBodyText());
    }

    private List<AiChatMessage> messages(AiChatSession session) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
    }

    private AiChatSession projectSession(UUID projectId, UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI chat session not found: " + sessionId));
        if (session.getScope() != AiChatScope.PROJECT
                || session.getProject() == null
                || !projectId.equals(session.getProject().getId())
                || session.getArchivedAt() != null) {
            throw new IllegalArgumentException("AI chat session not found in project " + projectId + ": " + sessionId);
        }
        return session;
    }

    private AiChatSession contactSession(UUID projectId, UUID contactId, UUID sessionId) {
        AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("AI chat session not found: " + sessionId));
        if (session.getScope() != AiChatScope.CONTACT
                || session.getProject() == null
                || session.getContact() == null
                || !projectId.equals(session.getProject().getId())
                || !contactId.equals(session.getContact().getId())
                || session.getArchivedAt() != null) {
            throw new IllegalArgumentException("AI chat session not found for contact " + contactId + ": " + sessionId);
        }
        return session;
    }

    private String defaultSessionTitle() {
        return "New chat " + OffsetDateTime.now().format(SESSION_TITLE_FORMAT);
    }

    private String validateSessionTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Chat title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_SESSION_TITLE_LENGTH) {
            throw new IllegalArgumentException("Chat title must be at most 120 characters");
        }
        return trimmed;
    }
}
