package br.com.saneia.api.sensor;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    /** Últimas leituras (ordem decrescente); o serviço inverte para ordem cronológica. */
    List<SensorReading> findBySensorIdOrderByMeasuredAtDescIdDesc(Long sensorId, Pageable pageable);

    List<SensorReading> findBySensorIdAndMeasuredAtGreaterThanEqualOrderByMeasuredAtDescIdDesc(
            Long sensorId, Instant from, Pageable pageable);

    @Query("select r from SensorReading r where r.sensor.id = :sensorId "
            + "and r.measuredAt >= :from and r.measuredAt <= :to order by r.measuredAt asc, r.id asc")
    List<SensorReading> findInRange(@Param("sensorId") Long sensorId, @Param("from") Instant from,
            @Param("to") Instant to, Pageable pageable);

    long countBySensorId(Long sensorId);
}
