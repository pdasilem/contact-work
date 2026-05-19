package com.pdasilem.contactwork.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    public static final UUID DEFAULT_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    @Column(name = "letter_template")
    private String letterTemplate;

    @Column(name = "mail_subject")
    private String mailSubject;

    @Column(name = "mail_body")
    private String mailBody;

    @Column(name = "letter_attachment_filename")
    private String letterAttachmentFilename;

    @Column(name = "mail_from")
    private String mailFrom;

    @Column(name = "mail_from_name")
    private String mailFromName;

    @Column(name = "send_delay_ms", nullable = false)
    private long sendDelayMs;

    @Column(name = "max_messages_per_batch")
    private Integer maxMessagesPerBatch;

    @Column(name = "inbox_sync_cron", nullable = false)
    private String inboxSyncCron;

    @Enumerated(EnumType.STRING)
    @Column(name = "mail_transport", nullable = false)
    private MailTransportType mailTransport = MailTransportType.BREVO;

    @Column(name = "gmail_username")
    private String gmailUsername;

    @Column(name = "gmail_app_password")
    private String gmailAppPassword;

    @Column(name = "ai_system_prompt")
    private String aiSystemPrompt;

    @Column(name = "last_mail_sync_at")
    private OffsetDateTime lastMailSyncAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ProjectStatus.NEW;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public String getLetterTemplate() {
        return letterTemplate;
    }

    public void setLetterTemplate(String letterTemplate) {
        this.letterTemplate = letterTemplate;
    }

    public String getMailSubject() {
        return mailSubject;
    }

    public void setMailSubject(String mailSubject) {
        this.mailSubject = mailSubject;
    }

    public String getMailBody() {
        return mailBody;
    }

    public void setMailBody(String mailBody) {
        this.mailBody = mailBody;
    }

    public String getLetterAttachmentFilename() {
        return letterAttachmentFilename;
    }

    public void setLetterAttachmentFilename(String letterAttachmentFilename) {
        this.letterAttachmentFilename = letterAttachmentFilename;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getMailFromName() {
        return mailFromName;
    }

    public void setMailFromName(String mailFromName) {
        this.mailFromName = mailFromName;
    }

    public long getSendDelayMs() {
        return sendDelayMs;
    }

    public void setSendDelayMs(long sendDelayMs) {
        this.sendDelayMs = sendDelayMs;
    }

    public Integer getMaxMessagesPerBatch() {
        return maxMessagesPerBatch;
    }

    public void setMaxMessagesPerBatch(Integer maxMessagesPerBatch) {
        if (maxMessagesPerBatch != null && maxMessagesPerBatch < 1) {
            throw new IllegalArgumentException("Max messages per batch must be at least 1");
        }
        this.maxMessagesPerBatch = maxMessagesPerBatch;
    }

    public String getInboxSyncCron() {
        return inboxSyncCron;
    }

    public void setInboxSyncCron(String inboxSyncCron) {
        this.inboxSyncCron = inboxSyncCron;
    }

    public MailTransportType getMailTransport() {
        return mailTransport;
    }

    public void setMailTransport(MailTransportType mailTransport) {
        this.mailTransport = mailTransport;
    }

    public String getGmailUsername() {
        return gmailUsername;
    }

    public void setGmailUsername(String gmailUsername) {
        this.gmailUsername = gmailUsername;
    }

    public String getGmailAppPassword() {
        return gmailAppPassword;
    }

    public void setGmailAppPassword(String gmailAppPassword) {
        this.gmailAppPassword = gmailAppPassword;
    }

    public String getAiSystemPrompt() {
        return aiSystemPrompt;
    }

    public void setAiSystemPrompt(String aiSystemPrompt) {
        this.aiSystemPrompt = aiSystemPrompt;
    }

    public OffsetDateTime getLastMailSyncAt() {
        return lastMailSyncAt;
    }

    public void setLastMailSyncAt(OffsetDateTime lastMailSyncAt) {
        this.lastMailSyncAt = lastMailSyncAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
