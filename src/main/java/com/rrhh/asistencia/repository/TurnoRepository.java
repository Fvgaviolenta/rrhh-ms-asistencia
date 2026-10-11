package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, String> {
    List<Turno> findByTenantIdAndActivoTrueOrderByNombreAsc(String tenantId);

    Optional<Turno> findByIdAndTenantId(String id, String tenantId);
}
