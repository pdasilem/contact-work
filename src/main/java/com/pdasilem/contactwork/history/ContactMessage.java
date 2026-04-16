package com.pdasilem.contactwork.history;

import com.pdasilem.contactwork.contact.Contact;
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
@Table(name = "contact_messages")
public class ContactMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private MessageEventType eventType;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "related_message_id")
    private String relatedMessageId;

    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "message_timestamp", nullable = false)
    private OffsetDateTime messageTimestamp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public MessageDirection getDirection() {
        return direction;
    }

    public void setDirection(MessageDirection direction) {
        this.direction = direction;
    }

    public MessageEventType getEventType() {
        return eventType;
    }

    public void setEventType(MessageEventType eventType) {
        this.eventType = eventType;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRelatedMessageId() {
        return relatedMessageId;
    }

    public void setRelatedMessageId(String relatedMessageId) {
        this.relatedMessageId = relatedMessageId;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public void setSenderEmail(String senderEmail) {
        this.senderEmail = senderEmail;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
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

    public OffsetDateTime getMessageTimestamp() {
        return messageTimestamp;
    }

    public void setMessageTimestamp(OffsetDateTime messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
