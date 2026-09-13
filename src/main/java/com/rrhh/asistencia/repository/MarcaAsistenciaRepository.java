package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.MarcaAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarcaAsistenciaRepository extends JpaRepository<MarcaAsistencia, String> {
    List<MarcaAsistencia> findByTenantIdAndActivoTrueOrderByFechaHoraDesc(String tenantId);
}
