package com.pdasilem.contactwork.contact;

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
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "organization_name", nullable = false)
    private String organizationName;

    @Column(name = "country")
    private String country;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "preclinical_notes")
    private String preclinicalNotes;

    @Column(name = "note")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStatus status;

    @Column(name = "outbound_message_id")
    private String outboundMessageId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "reply_received_at")
    private OffsetDateTime replyReceivedAt;

    @Column(name = "bounce_received_at")
    private OffsetDateTime bounceReceivedAt;

    @Column(name = "last_error_at")
    private OffsetDateTime lastErrorAt;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
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

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPreclinicalNotes() {
        return preclinicalNotes;
    }

    public void setPreclinicalNotes(String preclinicalNotes) {
        this.preclinicalNotes = preclinicalNotes;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public void setStatus(ContactStatus status) {
        this.status = status;
    }

    public String getOutboundMessageId() {
        return outboundMessageId;
    }

    public void setOutboundMessageId(String outboundMessageId) {
        this.outboundMessageId = outboundMessageId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(OffsetDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public OffsetDateTime getReplyReceivedAt() {
        return replyReceivedAt;
    }

    public void setReplyReceivedAt(OffsetDateTime replyReceivedAt) {
        this.replyReceivedAt = replyReceivedAt;
    }

    public OffsetDateTime getBounceReceivedAt() {
        return bounceReceivedAt;
    }

    public void setBounceReceivedAt(OffsetDateTime bounceReceivedAt) {
        this.bounceReceivedAt = bounceReceivedAt;
    }

    public OffsetDateTime getLastErrorAt() {
        return lastErrorAt;
    }

    public void setLastErrorAt(OffsetDateTime lastErrorAt) {
        this.lastErrorAt = lastErrorAt;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
