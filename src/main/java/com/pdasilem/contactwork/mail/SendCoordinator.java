package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.api.SendStatusResponse;
import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.template.TemplateService;
import java.time.OffsetDateTime;
import java.util.List;
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
    private final AppProperties appProperties;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SendCoordinator(
            ContactRepository contactRepository,
            TemplateService templateService,
            OutboundMailService outboundMailService,
            AppProperties appProperties,
            TaskExecutor taskExecutor
    ) {
        this.contactRepository = contactRepository;
        this.templateService = templateService;
        this.outboundMailService = outboundMailService;
        this.appProperties = appProperties;
        this.taskExecutor = taskExecutor;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Send process is already running");
        }
        taskExecutor.execute(this::runSendLoop);
    }

    public SendStatusResponse getStatus() {
        return new SendStatusResponse(
                running.get(),
                "BATCH endpoint processes NEW contacts only; single-contact endpoint can also retry SEND_FAILED contacts",
                contactRepository.countByStatus(ContactStatus.NEW),
                contactRepository.countByStatus(ContactStatus.NEW),
                contactRepository.countByStatus(ContactStatus.SENT),
                contactRepository.countByStatus(ContactStatus.SEND_FAILED),
                contactRepository.countByStatus(ContactStatus.BOUNCED),
                contactRepository.countByStatus(ContactStatus.REPLIED)
        );
    }

    private void runSendLoop() {
        try {
            List<Contact> contacts = contactRepository.findByStatusOrderByCreatedAtAsc(ContactStatus.NEW);
            for (Contact contact : contacts) {
                processContact(contact.getId());
                sleepDelay();
            }
        } finally {
            running.set(false);
        }
    }

    @Transactional
    public void processContact(java.util.UUID contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + contactId));
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
                    templateService.generateLetterPdf(contact.getContactName()),
                    templateService.getPitchDeckResource()
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

    private void sleepDelay() {
        try {
            Thread.sleep(Math.max(0, appProperties.mail().sendDelayMs()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Send loop interrupted", ex);
        }
    }

    public void sendSingle(java.util.UUID contactId) {
        processContact(contactId);
    }
}
