package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop20ByEstadoOrderByCreadoEnAsc(String estado);

    List<OutboxEvent> findByAggregateIdOrderByCreadoEnAsc(String aggregateId);
}
