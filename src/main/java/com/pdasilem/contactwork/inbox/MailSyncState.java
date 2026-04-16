package com.pdasilem.contactwork.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "mail_sync_state")
public class MailSyncState {

    @Id
    private Short id;

    @Column(name = "last_processed_uid", nullable = false)
    private long lastProcessedUid;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = OffsetDateTime.now();
    }

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public long getLastProcessedUid() {
        return lastProcessedUid;
    }

    public void setLastProcessedUid(long lastProcessedUid) {
        this.lastProcessedUid = lastProcessedUid;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
