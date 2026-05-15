package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.template.TemplateService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SendCoordinator {
    private static final Logger log = LoggerFactory.getLogger(SendCoordinator.class);

    private final ContactRepository contactRepository;
    private final TemplateService templateService;
    private final OutboundMailService outboundMailService;
    private final ProjectService projectService;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SendCoordinator(
            ContactRepository contactRepository,
            TemplateService templateService,
            OutboundMailService outboundMailService,
            ProjectService projectService,
            TaskExecutor taskExecutor
    ) {
        this.contactRepository = contactRepository;
        this.templateService = templateService;
        this.outboundMailService = outboundMailService;
        this.projectService = projectService;
        this.taskExecutor = taskExecutor;
    }

    public void start(UUID projectId) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Send process is already running");
        }
        taskExecutor.execute(() -> runSendLoop(projectId));
    }

    public SendStatusResponse getStatus(UUID projectId) {
        return new SendStatusResponse(
                running.get(),
                "BATCH endpoint processes NEW contacts only; single-contact endpoint can also retry SEND_FAILED contacts",
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.NEW),
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.NEW),
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.SENT),
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.SEND_FAILED),
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.BOUNCED),
                contactRepository.countByProjectIdAndStatus(projectId, ContactStatus.REPLIED)
        );
    }

    private void runSendLoop(UUID projectId) {
        try {
            Project project = projectService.getProject(projectId);
            List<Contact> contacts = contactRepository.findByProjectIdAndStatusOrderByCreatedAtAsc(projectId, ContactStatus.NEW);
            for (Contact contact : contacts) {
                processContact(projectId, contact.getId());
                sleepDelay(project);
            }
        } finally {
            running.set(false);
        }
    }

    @Transactional
    public void processContact(UUID projectId, UUID contactId) {
        Contact contact = contactRepository.findByProjectIdAndId(projectId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + contactId));
        if (contact.getStatus() != ContactStatus.NEW && contact.getStatus() != ContactStatus.SEND_FAILED) {
            return;
        }

        contact.setStatus(ContactStatus.IN_PROGRESS);
        contact.setLastErrorAt(null);
        contact.setLastErrorMessage(null);
        contactRepository.saveAndFlush(contact);

        try {
            String messageId = outboundMailService.send(
                    contact,
                    templateService.generateLetterPdf(contact.getProject(), contact.getContactName()),
                    templateService.getPitchDeckResource(contact.getProject())
            );
            contact.setOutboundMessageId(messageId);
            contact.setSentAt(OffsetDateTime.now());
            contact.setStatus(ContactStatus.SENT);
            contactRepository.save(contact);
            log.info("Sent email to {} with messageId={}", contact.getEmail(), messageId);
        } catch (Exception ex) {
            contact.setStatus(ContactStatus.SEND_FAILED);
            contact.setLastErrorAt(OffsetDateTime.now());
            contact.setLastErrorMessage(ex.getMessage());
            contactRepository.save(contact);
            log.warn("Failed to send email to {}: {}", contact.getEmail(), ex.getMessage());
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
        processContact(projectId, contactId);
    }
}
