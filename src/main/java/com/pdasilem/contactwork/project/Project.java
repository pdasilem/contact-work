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

    @Column(name = "letter_template", nullable = false)
    private String letterTemplate;

    @Column(name = "pitch_deck", nullable = false)
    private String pitchDeck;

    @Column(name = "mail_subject", nullable = false)
    private String mailSubject;

    @Column(name = "mail_body", nullable = false)
    private String mailBody;

    @Column(name = "letter_attachment_filename", nullable = false)
    private String letterAttachmentFilename;

    @Column(name = "pitch_deck_attachment_filename", nullable = false)
    private String pitchDeckAttachmentFilename;

    @Column(name = "mail_from")
    private String mailFrom;

    @Column(name = "send_delay_ms", nullable = false)
    private long sendDelayMs;

    @Column(name = "inbox_sync_cron", nullable = false)
    private String inboxSyncCron;

    @Column(name = "gmail_username")
    private String gmailUsername;

    @Column(name = "gmail_app_password")
    private String gmailAppPassword;

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
            status = ProjectStatus.ACTIVE;
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

    public String getPitchDeck() {
        return pitchDeck;
    }

    public void setPitchDeck(String pitchDeck) {
        this.pitchDeck = pitchDeck;
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

    public String getPitchDeckAttachmentFilename() {
        return pitchDeckAttachmentFilename;
    }

    public void setPitchDeckAttachmentFilename(String pitchDeckAttachmentFilename) {
        this.pitchDeckAttachmentFilename = pitchDeckAttachmentFilename;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public long getSendDelayMs() {
        return sendDelayMs;
    }

    public void setSendDelayMs(long sendDelayMs) {
        this.sendDelayMs = sendDelayMs;
    }

    public String getInboxSyncCron() {
        return inboxSyncCron;
    }

    public void setInboxSyncCron(String inboxSyncCron) {
        this.inboxSyncCron = inboxSyncCron;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
