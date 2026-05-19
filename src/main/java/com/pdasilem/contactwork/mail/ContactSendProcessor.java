package com.pdasilem.contactwork.mail;

import com.pdasilem.contactwork.config.AppProperties;
import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.contact.ContactRepository;
import com.pdasilem.contactwork.contact.ContactStatus;
import com.pdasilem.contactwork.project.MailTransportType;
import com.pdasilem.contactwork.project.Project;
import com.pdasilem.contactwork.project.ProjectService;
import com.pdasilem.contactwork.project.ProjectStatus;
import com.pdasilem.contactwork.project.asset.ProjectAssetService;
import com.pdasilem.contactwork.template.TemplateService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactSendProcessor {
    private static final Logger log = LoggerFactory.getLogger(ContactSendProcessor.class);
    private static final long STALE_IN_PROGRESS_MINUTES = 15;

    private final ContactRepository contactRepository;
    private final TemplateService templateService;
    private final OutboundMailService outboundMailService;
    private final ProjectService projectService;
    private final ProjectAssetService projectAssetService;
    private final AppProperties appProperties;

    public ContactSendProcessor(
            ContactRepository contactRepository,
            TemplateService templateService,
            OutboundMailService outboundMailService,
            ProjectService projectService,
            ProjectAssetService projectAssetService,
            AppProperties appProperties
    ) {
        this.contactRepository = contactRepository;
        this.templateService = templateService;
        this.outboundMailService = outboundMailService;
        this.projectService = projectService;
        this.projectAssetService = projectAssetService;
        this.appProperties = appProperties;
    }

    @Transactional
    public void processContact(UUID projectId, UUID contactId, boolean force) {
        processContact(projectId, contactId, force, false);
    }

    @Transactional
    public void processContactForSystem(UUID projectId, UUID contactId, boolean force) {
        processContact(projectId, contactId, force, true);
    }

    private void processContact(UUID projectId, UUID contactId, boolean force, boolean systemAccess) {
        Contact contact = contactRepository.findByProjectIdAndId(projectId, contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found in project " + projectId + ": " + contactId));
        Project project = requireActiveProject(projectId, systemAccess);
        if (!force && contact.getStatus() != ContactStatus.NEW && contact.getStatus() != ContactStatus.SEND_FAILED) {
            return;
        }

        log.info("Marking contact {} IN_PROGRESS", contactId);
        contact.setStatus(ContactStatus.IN_PROGRESS);
        contact.setLastErrorAt(null);
        contact.setLastErrorMessage(null);
        contactRepository.saveAndFlush(contact);

        try {
            log.info("Generating PDF for contact {}", contactId);
            var generatedLetter = templateService.generateLetterPdf(
                    project,
                    systemAccess
                            ? projectAssetService.activeLetterResourceForSystem(projectId)
                            : projectAssetService.activeLetterResource(projectId),
                    contact.getContactName()
            );
            log.info("Generated PDF for contact {}", contactId);

            log.info("Sending SMTP message for contact {}", contactId);
            String messageId = outboundMailService.send(
                    project,
                    contact,
                    generatedLetter,
                    systemAccess
                            ? projectAssetService.activeMailAttachmentsForSystem(projectId)
                            : projectAssetService.activeMailAttachments(projectId)
            );
            log.info("Sent SMTP message for contact {} messageId={}", contactId, messageId);

            contact.setOutboundMessageId(messageId);
            contact.setSentAt(OffsetDateTime.now());
            contact.setStatus(ContactStatus.SENT);
            contactRepository.save(contact);
            log.info("Marked contact {} SENT", contactId);
        } catch (Exception ex) {
            contact.setStatus(ContactStatus.SEND_FAILED);
            contact.setLastErrorAt(OffsetDateTime.now());
            contact.setLastErrorMessage(ex.getMessage());
            contactRepository.save(contact);
            log.warn("Marked contact {} SEND_FAILED: {}", contactId, ex.getMessage());
        }
    }

    @Transactional
    public int recoverStuckInProgress(UUID projectId) {
        List<Contact> contacts = contactRepository.findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(
                projectId,
                ContactStatus.IN_PROGRESS
        );
        OffsetDateTime staleBefore = OffsetDateTime.now().minusMinutes(STALE_IN_PROGRESS_MINUTES);
        int repaired = 0;
        for (Contact contact : contacts) {
            if (contact.getOutboundMessageId() != null && contact.getSentAt() != null) {
                contact.setStatus(ContactStatus.SENT);
                contactRepository.save(contact);
                repaired++;
                log.info("Recovered contact {} from IN_PROGRESS to SENT", contact.getId());
            } else if (isStale(contact, staleBefore)) {
                contact.setStatus(ContactStatus.SEND_FAILED);
                contact.setLastErrorAt(OffsetDateTime.now());
                contact.setLastErrorMessage("Send left IN_PROGRESS for more than " + STALE_IN_PROGRESS_MINUTES + " minutes");
                contactRepository.save(contact);
                repaired++;
                log.warn("Recovered stale contact {} from IN_PROGRESS to SEND_FAILED", contact.getId());
            }
        }
        return repaired;
    }

    private boolean isStale(Contact contact, OffsetDateTime staleBefore) {
        OffsetDateTime updatedAt = contact.getUpdatedAt();
        return updatedAt != null && updatedAt.isBefore(staleBefore);
    }

    private Project requireActiveProject(UUID projectId) {
        return requireActiveProject(projectId, false);
    }

    private Project requireActiveProject(UUID projectId, boolean systemAccess) {
        Project project = systemAccess ? projectService.getProjectForSystem(projectId) : projectService.getProject(projectId);
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
        if (systemAccess) {
            projectAssetService.activeLetterForSystem(projectId)
                    .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
        } else {
            projectAssetService.activeLetter(projectId)
                    .orElseThrow(() -> new IllegalStateException("Project has no active letter template"));
        }
        return project;
    }
}
