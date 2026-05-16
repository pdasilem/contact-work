package com.pdasilem.contactwork.contact;

import com.pdasilem.contactwork.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "project_contact_columns")
public class ProjectContactColumn {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "column_key", nullable = false)
    private String columnKey;

    @Column(name = "display_label", nullable = false)
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private ContactColumnSource sourceType;

    @Column(name = "column_order", nullable = false)
    private int columnOrder;

    @Column(name = "visible", nullable = false)
    private boolean visible = true;

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

    public String getColumnKey() {
        return columnKey;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public ContactColumnSource getSourceType() {
        return sourceType;
    }

    public void setSourceType(ContactColumnSource sourceType) {
        this.sourceType = sourceType;
    }

    public int getColumnOrder() {
        return columnOrder;
    }

    public void setColumnOrder(int columnOrder) {
        this.columnOrder = columnOrder;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
