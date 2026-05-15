package com.pdasilem.contactwork.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.pdasilem.contactwork.project.Project;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mail_sync_state")
public class MailSyncState {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "last_processed_uid", nullable = false)
    private long lastProcessedUid;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
        this.projectId = project == null ? null : project.getId();
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
