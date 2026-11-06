package br.com.saneia.api.alert;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Page<EmailLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByStatusAndCreatedAtGreaterThanEqual(EmailLog.Status status, Instant since);
}
