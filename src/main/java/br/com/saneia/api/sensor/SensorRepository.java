package br.com.saneia.api.sensor;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorRepository extends JpaRepository<Sensor, Long> {

    Page<Sensor> findByActiveTrueOrderByPrefixAsc(Pageable pageable);

    List<Sensor> findByActiveTrue();

    Optional<Sensor> findByIdAndActiveTrue(Long id);

    Optional<Sensor> findFirstByPrefixIgnoreCaseAndActiveTrue(String prefix);

    boolean existsByPrefixIgnoreCaseAndActiveTrue(String prefix);

    boolean existsByPrefixIgnoreCaseAndActiveTrueAndIdNot(String prefix, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sensor s where s.id = :id")
    Optional<Sensor> lockById(@Param("id") Long id);

    /** Ativos sem a chave de unicidade: só existem em bancos criados antes da restrição de prefixo. */
    List<Sensor> findByActiveTrueAndActivePrefixKeyIsNullOrderByIdAsc();

    boolean existsByActivePrefixKey(String activePrefixKey);

    @Modifying
    @Query("update Sensor s set s.activePrefixKey = :key where s.id = :id")
    int assignActivePrefixKey(@Param("id") Long id, @Param("key") String key);

    /** Equipamentos ativos que já enviaram leitura e ainda não estão marcados como sem comunicação. */
    List<Sensor> findByActiveTrueAndLastReadingAtIsNotNullAndStatusNot(SensorStatus status);
}
