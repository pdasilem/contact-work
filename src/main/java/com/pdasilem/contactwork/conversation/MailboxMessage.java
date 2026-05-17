package com.pdasilem.contactwork.conversation;

import com.pdasilem.contactwork.contact.Contact;
import com.pdasilem.contactwork.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mailbox_messages")
public class MailboxMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "folder", nullable = false)
    private MailboxFolder folder;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MailboxDirection direction;

    @Column(name = "service_date", nullable = false)
    private OffsetDateTime serviceDate;

    @Column(name = "normalized_message_id")
    private String normalizedMessageId;

    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "recipient_emails")
    private String recipientEmails;

    @Column(name = "cc_emails")
    private String ccEmails;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public MailboxFolder getFolder() {
        return folder;
    }

    public void setFolder(MailboxFolder folder) {
        this.folder = folder;
    }

    public MailboxDirection getDirection() {
        return direction;
    }

    public void setDirection(MailboxDirection direction) {
        this.direction = direction;
    }

    public OffsetDateTime getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(OffsetDateTime serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getNormalizedMessageId() {
        return normalizedMessageId;
    }

    public void setNormalizedMessageId(String normalizedMessageId) {
        this.normalizedMessageId = normalizedMessageId;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getRecipientEmails() {
        return recipientEmails;
    }

    public void setRecipientEmails(String recipientEmails) {
        this.recipientEmails = recipientEmails;
    }

    public String getCcEmails() {
        return ccEmails;
    }

    public void setCcEmails(String ccEmails) {
        this.ccEmails = ccEmails;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyText() {
        return bodyText;
    }

    public void setBodyText(String bodyText) {
        this.bodyText = bodyText;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
