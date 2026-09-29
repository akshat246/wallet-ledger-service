package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notifications_user_id", columnList = "user_id"),
    @Index(name = "idx_notifications_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_notifications_status", columnList = "status"),
    @Index(name = "idx_notifications_created_at", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"user", "transaction"})
@EqualsAndHashCode(exclude = {"user", "transaction"})
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_user"))
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", foreignKey = @ForeignKey(name = "fk_notification_transaction"))
    private Transaction transaction;
    
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;
    
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;
    
    @Column(nullable = false, length = 255)
    private String recipient;
    
    @Lob
    @Column(columnDefinition = "TEXT")
    private String messageContent;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column
    private LocalDateTime lastRetryAt;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * Check if notification was sent
     */
    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }
    
    /**
     * Check if notification failed
     */
    public boolean hasFailed() {
        return status == NotificationStatus.FAILED;
    }
    
    /**
     * Increment retry count
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
    }
    
    /**
     * Notification type enumeration
     */
    public enum NotificationType {
        EMAIL,
        SMS,
        IN_APP
    }
    
    /**
     * Notification status enumeration
     */
    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED
    }
}