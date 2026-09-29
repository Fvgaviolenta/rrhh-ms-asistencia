package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.dto.request.RegistrarMarcaRequest;
import com.rrhh.asistencia.dto.response.AsistenciaResumenResponse;
import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.MarcaAsistencia;
import com.rrhh.asistencia.model.TipoMarca;
import com.rrhh.asistencia.repository.MarcaAsistenciaRepository;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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

    public List<MarcaAsistenciaResponse> listarPorTrabajador(String trabajadorId) {
        String tenantId = tenantContext.require().tenantId();
        return marcaAsistenciaRepository.findByTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(trabajadorId)
                .stream()
                .filter(m -> m.getTenantId().equals(tenantId))
                .map(this::toResponse)
                .toList();
    }

    public MarcaAsistenciaResponse obtenerMarcaHoy(String trabajadorId) {
        tenantContext.require().tenantId(); // Validar tenant
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Santiago"));
        
        List<MarcaAsistencia> marcasHoy = marcaAsistenciaRepository.findByTrabajadorIdAndFechaHoraBetweenAndActivoTrue(
                trabajadorId,
                hoy.atStartOfDay(ZoneId.of("America/Santiago")).toInstant(),
                hoy.plusDays(1).atStartOfDay(ZoneId.of("America/Santiago")).toInstant()
        );
        
        if (marcasHoy.isEmpty()) {
            throw new DomainException(404, "No hay marcas registradas hoy para este trabajador");
        }
        
        return toResponse(marcasHoy.get(0));
    }

    public List<MarcaAsistenciaResponse> listarPorPeriodo(String trabajadorId, LocalDate fechaInicio, LocalDate fechaFin) {
        String tenantId = tenantContext.require().tenantId();
        
        Instant inicio = fechaInicio.atStartOfDay(ZoneId.of("America/Santiago")).toInstant();
        Instant fin = fechaFin.plusDays(1).atStartOfDay(ZoneId.of("America/Santiago")).toInstant();
        
        return marcaAsistenciaRepository.findByTrabajadorIdAndFechaHoraBetweenAndActivoTrue(
                trabajadorId, inicio, fin)
                .stream()
                .filter(m -> m.getTenantId().equals(tenantId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MarcaAsistenciaResponse registrar(RegistrarMarcaRequest request) {
        String tenantId = tenantContext.require().tenantId();
        
        // Validar que no exista una marca del mismo tipo en los últimos 5 minutos
        Instant hace5Minutos = Instant.now().minusSeconds(300);
        List<MarcaAsistencia> marcasRecientes = marcaAsistenciaRepository
                .findByTrabajadorIdAndFechaHoraBetweenAndActivoTrue(
                        request.trabajadorId(),
                        hace5Minutos,
                        Instant.now()
                );
        
        TipoMarca tipoEnum = TipoMarca.valueOf(request.tipo().name());
        
        boolean marcaDuplicada = marcasRecientes.stream()
                .anyMatch(m -> m.getTipo() == tipoEnum);
        
        if (marcaDuplicada) {
            throw new DomainException(409, "Ya existe una marca de " + request.tipo().name().toLowerCase() + " registrada recientemente");
        }
        
        MarcaAsistencia marca = new MarcaAsistencia();
        marca.setId(UUID.randomUUID().toString());
        marca.setTenantId(tenantId);
        marca.setTrabajadorId(request.trabajadorId());
        marca.setTipo(tipoEnum);
        marca.setFechaHora(Instant.now());
        marca.setOrigen(request.origen());
        marca.setActivo(true);
        marca.setCreadoEn(Instant.now());
        
        marcaAsistenciaRepository.save(marca);
        
        return toResponse(marca);
    }

    @Transactional
    public MarcaAsistenciaResponse editar(String marcaId, EditarMarcaRequest request) {
        String tenantId = tenantContext.require().tenantId();
        
        MarcaAsistencia marca = marcaAsistenciaRepository.findById(marcaId)
                .orElseThrow(() -> new DomainException(404, "Marca no encontrada"));
        
        if (!marca.getTenantId().equals(tenantId)) {
            throw new DomainException(403, "No tienes permiso para editar esta marca");
        }
        
        // TODO: Guardar valor anterior para auditoría en audit_log
        // Instant fechaHoraAnterior = marca.getFechaHora();
        
        marca.setFechaHora(request.fechaHora());
        marcaAsistenciaRepository.save(marca);
        
        // TODO: Publicar evento de edición para auditoría en RabbitMQ
        
        return toResponse(marca);
    }

    public AsistenciaResumenResponse resumen(LocalDate fechaInicio, LocalDate fechaFin) {
        String tenantId = tenantContext.require().tenantId();
        
        // Calcular resumen por día
        LocalDate fechaActual = fechaInicio;
        long totalMarcas = 0;
        long totalEntradas = 0;
        long totalSalidas = 0;
        
        while (!fechaActual.isAfter(fechaFin)) {
            totalMarcas += marcaAsistenciaRepository.countByTenantIdAndFechaAndActivoTrue(tenantId, fechaActual);
            totalEntradas += marcaAsistenciaRepository.countByTenantIdAndFechaAndTipoAndActivoTrue(
                    tenantId, fechaActual, TipoMarca.ENTRADA);
            totalSalidas += marcaAsistenciaRepository.countByTenantIdAndFechaAndTipoAndActivoTrue(
                    tenantId, fechaActual, TipoMarca.SALIDA);
            fechaActual = fechaActual.plusDays(1);
        }
        
        // TODO: Obtener total de trabajadores activos del tenant
        long totalTrabajadores = 0; // Pendiente: llamar a MS Trabajadores
        
        // Calcular porcentaje de asistencia
        BigDecimal porcentajeAsistencia = totalTrabajadores > 0 
                ? BigDecimal.valueOf(totalEntradas)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalTrabajadores * (long)(fechaFin.toEpochDay() - fechaInicio.toEpochDay() + 1)), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        
        return new AsistenciaResumenResponse(
                fechaInicio,
                totalTrabajadores,
                totalMarcas,
                totalEntradas,
                totalSalidas,
                porcentajeAsistencia
        );
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
