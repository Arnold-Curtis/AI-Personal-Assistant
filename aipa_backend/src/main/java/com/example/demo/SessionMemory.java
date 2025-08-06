package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "session_memories")
public class SessionMemory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "chat_count", nullable = false)
    private Integer chatCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_activity", nullable = false)
    private LocalDateTime lastActivity;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_context_sent_at")
    private LocalDateTime lastContextSentAt;

    
    public SessionMemory() {
        this.createdAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    public SessionMemory(User user, String sessionId) {
        this();
        this.user = user;
        this.sessionId = sessionId;
    }

    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getChatCount() {
        return chatCount;
    }

    public void setChatCount(Integer chatCount) {
        this.chatCount = chatCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getLastContextSentAt() {
        return lastContextSentAt;
    }

    public void setLastContextSentAt(LocalDateTime lastContextSentAt) {
        this.lastContextSentAt = lastContextSentAt;
    }

    
    public void incrementChatCount() {
        this.chatCount++;
        this.lastActivity = LocalDateTime.now();
    }

    public boolean shouldSendContext() {
        
        return this.chatCount == 0 || this.chatCount % 10 == 0;
    }

    public void markContextSent() {
        this.lastContextSentAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "SessionMemory{" +
                "id=" + id +
                ", sessionId='" + sessionId + '\'' +
                ", chatCount=" + chatCount +
                ", createdAt=" + createdAt +
                ", lastActivity=" + lastActivity +
                ", isActive=" + isActive +
                '}';
    }
}

