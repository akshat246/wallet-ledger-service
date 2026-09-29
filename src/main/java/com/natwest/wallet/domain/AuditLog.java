package com.natwest.wallet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_entity", columnList = "entity_type,entity_id"),
    @Index(name = "idx_audit_logs_action", columnList = "action"),
    @Index(name = "idx_audit_logs_created_at", columnList = "created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "UUID")
    private UUID id;
    
    /**
     * Type of entity that was modified (USER, WALLET, TRANSACTION, etc.)
     */
    @Column(nullable = false, length = 100)
    private String entityType;
    
    /**
     * ID of the entity that was modified
     */
    @Column(nullable = false, columnDefinition = "UUID")
    private UUID entityId;
    
    /**
     * Action performed (CREATE, UPDATE, DELETE)
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditAction action;
    
    /**
     * Who performed the action (user ID or system)
     */
    @Column(length = 255)
    private String actor;
    
    /**
     * Changes made (before/after values as JSON)
     */
    @Lob
    @Column(columnDefinition = "JSONB")
    private String changes;
    
    /**
     * IP address of the request
     */
    @Column
    private String ipAddress;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Audit action enumeration
     */
    public enum AuditAction {
        CREATE,
        UPDATE,
        DELETE
    }
}