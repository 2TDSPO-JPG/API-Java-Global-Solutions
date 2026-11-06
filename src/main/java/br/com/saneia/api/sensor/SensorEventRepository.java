package br.com.saneia.api.sensor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorEventRepository extends JpaRepository<SensorEvent, Long> {

    Optional<SensorEvent> findFirstBySensorIdAndKindAndEndedAtIsNullOrderByStartedAtDesc(Long sensorId, EventKind kind);

    List<SensorEvent> findBySensorIdOrderByStartedAtDescIdDesc(Long sensorId, Pageable pageable);

    long countBySensorIdAndKindAndSeverityAndStartedAtGreaterThanEqual(Long sensorId, EventKind kind,
            SensorStatus severity, Instant since);

    /** Eventos que se sobrepõem ao intervalo pedido (para os insights). */
    @Query("select e from SensorEvent e where e.sensor.id = :sensorId and e.startedAt <= :to "
            + "and (e.endedAt is null or e.endedAt >= :from) order by e.startedAt asc")
    List<SensorEvent> findOverlapping(@Param("sensorId") Long sensorId, @Param("from") Instant from,
            @Param("to") Instant to);
}
