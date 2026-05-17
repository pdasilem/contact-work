package com.pdasilem.contactwork.ai;

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
import com.pdasilem.contactwork.project.AiProvider;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
public class AiChatMessage {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AiChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AiChatRole role;

    @Column(name = "content", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private AiProvider provider;

    @Column(name = "model")
    private String model;

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

    public AiChatSession getSession() {
        return session;
    }

    public void setSession(AiChatSession session) {
        this.session = session;
    }

    public AiChatRole getRole() {
        return role;
    }

    public void setRole(AiChatRole role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
