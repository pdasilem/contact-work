package com.pdasilem.contactwork.ai;

import com.pdasilem.contactwork.project.AiProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "app_ai_settings")
public class AppAiSettings {
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private AiProvider provider;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = SINGLETON_ID;
        }
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public AiProvider getProvider() {
        return provider;
    }

    public void setProvider(AiProvider provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
