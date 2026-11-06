package br.com.saneia.api.alert;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRecipientRepository extends JpaRepository<AlertRecipient, Long> {

    List<AlertRecipient> findAllByOrderByNameAsc();

    List<AlertRecipient> findByActiveTrue();

    boolean existsByEmailIgnoreCase(String email);
}
