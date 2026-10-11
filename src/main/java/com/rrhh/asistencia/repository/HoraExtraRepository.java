package com.rrhh.asistencia.repository;

import com.rrhh.asistencia.model.EstadoHoraExtra;
import com.rrhh.asistencia.model.HoraExtra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HoraExtraRepository extends JpaRepository<HoraExtra, String> {
    List<HoraExtra> findByTenantIdAndPeriodoOrderByFechaDesc(String tenantId, String periodo);
    List<HoraExtra> findByTenantIdAndTrabajadorIdAndPeriodoOrderByFechaDesc(String tenantId, String trabajadorId, String periodo);
    List<HoraExtra> findByTenantIdAndTrabajadorIdAndPeriodoAndEstado(String tenantId, String trabajadorId, String periodo, EstadoHoraExtra estado);
    Optional<HoraExtra> findByIdAndTenantId(String id, String tenantId);
    List<HoraExtra> findByIdInAndTenantIdAndEstado(Collection<String> ids, String tenantId, EstadoHoraExtra estado);
}
