package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.MarcaAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarcaAsistenciaRepository extends JpaRepository<MarcaAsistencia, String> {
    List<MarcaAsistencia> findByTenantIdAndActivoTrueOrderByFechaHoraDesc(String tenantId);

    List<MarcaAsistencia> findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(String tenantId, String trabajadorId);

    List<MarcaAsistencia> findByTenantIdAndTrabajadorIdAndActivoTrueAndFechaHoraBetweenOrderByFechaHoraDesc(
            String tenantId, String trabajadorId, Instant desde, Instant hasta);

    List<MarcaAsistencia> findByTenantIdAndActivoTrueAndFechaHoraBetween(String tenantId, Instant desde, Instant hasta);

    Optional<MarcaAsistencia> findByIdAndTenantIdAndActivoTrue(String id, String tenantId);
}
