package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class SendCoordinator {
    private final ContactRepository contactRepository;
    private final ProjectService projectService;
    private final ProjectAssetService projectAssetService;
    private final ContactSendProcessor contactSendProcessor;
    private final TaskExecutor taskExecutor;
    private final AppProperties appProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SendCoordinator(
            ContactRepository contactRepository,
            ProjectService projectService,
            ProjectAssetService projectAssetService,
            ContactSendProcessor contactSendProcessor,
            TaskExecutor taskExecutor,
            AppProperties appProperties
    ) {
        this.contactRepository = contactRepository;
        this.projectService = projectService;
        this.projectAssetService = projectAssetService;
        this.contactSendProcessor = contactSendProcessor;
        this.taskExecutor = taskExecutor;
        this.appProperties = appProperties;
    }

    public void start(UUID projectId) {
        start(projectId, List.of(ContactStatus.NEW));
    }

    public void start(UUID projectId, List<ContactStatus> statuses) {
        requireActiveProject(projectId);
        List<ContactStatus> batchStatuses = batchStatuses(statuses);
        contactSendProcessor.recoverStuckInProgress(projectId);
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Send process is already running");
        }
        taskExecutor.execute(() -> runSendLoop(projectId, batchStatuses));
    }

    public SendStatusResponse getStatus(UUID projectId) {
        Project project = projectService.getProject(projectId);
        contactSendProcessor.recoverStuckInProgress(projectId);
        long newCount = contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.NEW);
        long eligibleBatchCount = cappedCount(newCount, project.getMaxMessagesPerBatch());
        return new SendStatusResponse(
                running.get(),
                "BATCH endpoint processes selected contact statuses; default is NEW",
                newCount,
                eligibleBatchCount,
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.SENT),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.SEND_FAILED),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.BOUNCED),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.REPLIED)
        );
    }

    private void runSendLoop(UUID projectId, List<ContactStatus> statuses) {
        try {
            Project project = projectService.getProjectForSystem(projectId);
            List<Contact> contacts = contactRepository.findByProjectIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtAsc(projectId, statuses);
            Integer maxMessagesPerBatch = project.getMaxMessagesPerBatch();
            if (maxMessagesPerBatch != null) {
                contacts = contacts.stream()
                        .limit(maxMessagesPerBatch)
                        .toList();
            }
            for (Contact contact : contacts) {
                contactSendProcessor.processContactForSystem(projectId, contact.getId(), forceForBatch(contact.getStatus()));
                sleepDelay(project);
            }
        } finally {
            running.set(false);
        }
    }

    private List<ContactStatus> batchStatuses(List<ContactStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(ContactStatus.NEW);
        }
        List<ContactStatus> batchStatuses = statuses.stream()
                .filter(status -> status != null)
                .distinct()
                .toList();
        if (batchStatuses.isEmpty()) {
            return List.of(ContactStatus.NEW);
        }
        if (batchStatuses.contains(ContactStatus.IN_PROGRESS) || batchStatuses.contains(ContactStatus.INVALID_EMAIL)) {
            throw new IllegalArgumentException("IN_PROGRESS and INVALID_EMAIL contacts cannot be selected for batch sending");
        }
        return batchStatuses;
    }

    private boolean forceForBatch(ContactStatus status) {
        return status != ContactStatus.NEW && status != ContactStatus.SEND_FAILED;
    }

    private void sleepDelay(Project project) {
        try {
            Thread.sleep(Math.max(0, project.getSendDelayMs()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Send loop interrupted", ex);
        }
    }

    public void sendSingle(UUID projectId, UUID contactId) {
        sendSingle(projectId, contactId, false);
    }

    public void sendSingle(UUID projectId, UUID contactId, boolean force) {
        contactSendProcessor.processContact(projectId, contactId, force);
    }

    private long cappedCount(long count, Integer maxMessagesPerBatch) {
        if (maxMessagesPerBatch == null) {
            return count;
        }
        return Math.min(count, maxMessagesPerBatch);
    }

    private Project requireActiveProject(UUID projectId) {
        Project project = projectService.getProject(projectId);
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE projects may send email");
        }
        if (project.getMailSubject() == null || project.getMailSubject().isBlank()) {
            throw new IllegalStateException("Project email subject is required before sending");
        }
        if (project.getMailBody() == null || project.getMailBody().isBlank()) {
            throw new IllegalStateException("Project email body is required before sending");
        }
        if (project.getMailTransport() == MailTransportType.GMAIL) {
            if (project.getGmailUsername() == null || project.getGmailUsername().isBlank()
                    || project.getGmailAppPassword() == null || project.getGmailAppPassword().isBlank()) {
                throw new IllegalStateException("Project Gmail credentials are required before sending");
            }
        } else {
            String brevoKey = appProperties.mail().brevo().apiKey();
            if (brevoKey == null || brevoKey.isBlank()) {
                throw new IllegalStateException("BREVO_API_KEY environment variable is not configured");
            }
        }
        projectAssetService.activeLetter(projectId)
                .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
        return project;
    }
}
