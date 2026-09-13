package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.model.MarcaAsistencia;
import com.rrhh.asistencia.repository.MarcaAsistenciaRepository;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MarcaAsistenciaService {
    private final MarcaAsistenciaRepository marcaAsistenciaRepository;
    private final TenantContext tenantContext;

    public MarcaAsistenciaService(MarcaAsistenciaRepository marcaAsistenciaRepository, TenantContext tenantContext) {
        this.marcaAsistenciaRepository = marcaAsistenciaRepository;
        this.tenantContext = tenantContext;
    }

    public List<MarcaAsistenciaResponse> listarPorTenant() {
        String tenantId = tenantContext.require().tenantId();
        return marcaAsistenciaRepository.findByTenantIdAndActivoTrueOrderByFechaHoraDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MarcaAsistenciaResponse toResponse(MarcaAsistencia marca) {
        return new MarcaAsistenciaResponse(
                marca.getId(),
                marca.getTenantId(),
                marca.getTrabajadorId(),
                marca.getTipo(),
                marca.getFechaHora(),
                marca.getOrigen(),
                marca.isActivo(),
                marca.getCreadoEn()
        );
    }
}
