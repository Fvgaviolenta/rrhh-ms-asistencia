package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.MarcaAsistencia;
import com.rrhh.asistencia.model.TipoMarca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface MarcaAsistenciaRepository extends JpaRepository<MarcaAsistencia, String> {
    List<MarcaAsistencia> findByTenantIdAndActivoTrueOrderByFechaHoraDesc(String tenantId);

    List<MarcaAsistencia> findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(
            String tenantId, String trabajadorId);

    List<MarcaAsistencia> findByTenantIdAndTrabajadorIdAndFechaHoraBetweenAndActivoTrue(
            String tenantId,
            String trabajadorId,
            Instant fechaInicio,
            Instant fechaFin
    );

    java.util.Optional<MarcaAsistencia> findByIdAndTenantId(String id, String tenantId);

    @Query("SELECT m FROM MarcaAsistencia m WHERE m.tenantId = :tenantId AND m.activo = true " +
           "AND DATE(m.fechaHora) = :fecha ORDER BY m.fechaHora DESC")
    List<MarcaAsistencia> findByTenantIdAndFechaAndActivoTrue(
            @Param("tenantId") String tenantId,
            @Param("fecha") LocalDate fecha
    );

    @Query("SELECT COUNT(m) FROM MarcaAsistencia m WHERE m.tenantId = :tenantId AND m.activo = true " +
           "AND DATE(m.fechaHora) = :fecha")
    Long countByTenantIdAndFechaAndActivoTrue(
            @Param("tenantId") String tenantId,
            @Param("fecha") LocalDate fecha
    );

    @Query("SELECT COUNT(m) FROM MarcaAsistencia m WHERE m.tenantId = :tenantId AND m.activo = true " +
           "AND DATE(m.fechaHora) = :fecha AND m.tipo = :tipo")
    Long countByTenantIdAndFechaAndTipoAndActivoTrue(
            @Param("tenantId") String tenantId,
            @Param("fecha") LocalDate fecha,
            @Param("tipo") TipoMarca tipo
    );
}
