package com.natwest.wallet.repository;

import com.natwest.wallet.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByStatusOrderByCreatedAtAsc(Notification.NotificationStatus status);
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
}