package com.example.booking.repository;

import com.example.booking.model.Notification;
import com.example.booking.notification.TypeNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByReservationIdAndType(Long reservationId, TypeNotification type);

    List<Notification> findByReservationIdOrderByCreeeLeAsc(Long reservationId);
}
