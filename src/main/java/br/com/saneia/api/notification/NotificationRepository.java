package br.com.saneia.api.notification;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.saneia.api.user.Role;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = "select n from Notification n where n.recipientUserId = :userId or n.recipientRole = :role "
            + "order by n.createdAt desc, n.id desc",
            countQuery = "select count(n) from Notification n where n.recipientUserId = :userId or n.recipientRole = :role")
    Page<Notification> findForRecipient(@Param("userId") Long userId, @Param("role") Role role, Pageable pageable);

    @Query("select n from Notification n where n.id = :id and (n.recipientUserId = :userId or n.recipientRole = :role)")
    Optional<Notification> findForRecipient(@Param("id") Long id, @Param("userId") Long userId, @Param("role") Role role);
}
