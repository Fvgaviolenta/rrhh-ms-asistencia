package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.Jornada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JornadaRepository extends JpaRepository<Jornada, String> {
    List<Jornada> findByTenantIdAndActivoTrueOrderByFechaAsc(String tenantId);

    List<Jornada> findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaAsc(String tenantId, String trabajadorId);

    Optional<Jornada> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndTrabajadorIdAndFechaAndActivoTrue(String tenantId, String trabajadorId, LocalDate fecha);
}
