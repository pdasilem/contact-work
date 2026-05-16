package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
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
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SendCoordinator(
            ContactRepository contactRepository,
            ProjectService projectService,
            ProjectAssetService projectAssetService,
            ContactSendProcessor contactSendProcessor,
            TaskExecutor taskExecutor
    ) {
        this.contactRepository = contactRepository;
        this.projectService = projectService;
        this.projectAssetService = projectAssetService;
        this.contactSendProcessor = contactSendProcessor;
        this.taskExecutor = taskExecutor;
    }

    public void start(UUID projectId) {
        requireActiveProject(projectId);
        contactSendProcessor.recoverStuckInProgress(projectId);
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Send process is already running");
        }
        taskExecutor.execute(() -> runSendLoop(projectId));
    }

    public SendStatusResponse getStatus(UUID projectId) {
        Project project = projectService.getProject(projectId);
        contactSendProcessor.recoverStuckInProgress(projectId);
        long newCount = contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.NEW);
        long eligibleBatchCount = cappedCount(newCount, project.getMaxMessagesPerBatch());
        return new SendStatusResponse(
                running.get(),
                "BATCH endpoint processes NEW contacts only; single-contact endpoint can also retry SEND_FAILED contacts",
                newCount,
                eligibleBatchCount,
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.SENT),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.SEND_FAILED),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.BOUNCED),
                contactRepository.countByProjectIdAndStatusAndDeletedAtIsNull(projectId, ContactStatus.REPLIED)
        );
    }

    private void runSendLoop(UUID projectId) {
        try {
            Project project = projectService.getProject(projectId);
            List<Contact> contacts = contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(projectId, ContactStatus.NEW);
            Integer maxMessagesPerBatch = project.getMaxMessagesPerBatch();
            if (maxMessagesPerBatch != null) {
                contacts = contacts.stream()
                        .limit(maxMessagesPerBatch)
                        .toList();
            }
            for (Contact contact : contacts) {
                contactSendProcessor.processContact(projectId, contact.getId(), false);
                sleepDelay(project);
            }
        } finally {
            running.set(false);
        }
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
        if (project.getGmailUsername() == null || project.getGmailUsername().isBlank()
                || project.getGmailAppPassword() == null || project.getGmailAppPassword().isBlank()) {
            throw new IllegalStateException("Project Gmail credentials are required before sending");
        }
        projectAssetService.activeLetter(projectId)
                .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
        return project;
    }
}
