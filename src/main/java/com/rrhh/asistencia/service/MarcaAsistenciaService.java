package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.dto.request.RegistrarMarcaRequest;
import com.rrhh.asistencia.dto.response.AsistenciaResumenResponse;
import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.MarcaAsistencia;
import com.rrhh.asistencia.model.TipoMarca;
import com.rrhh.asistencia.repository.MarcaAsistenciaRepository;
import com.rrhh.asistencia.security.Roles;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class MarcaAsistenciaService {
    private static final ZoneId ZONA = ZoneId.of("America/Santiago");

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
        return marcaAsistenciaRepository
                .findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(tenantId, trabajadorId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MarcaAsistenciaResponse marcaDeHoy(String trabajadorId) {
        String tenantId = tenantContext.require().tenantId();
        LocalDate hoy = LocalDate.now(ZONA);
        Instant desde = hoy.atStartOfDay(ZONA).toInstant();
        Instant hasta = hoy.plusDays(1).atStartOfDay(ZONA).toInstant();
        return marcaAsistenciaRepository
                .findByTenantIdAndTrabajadorIdAndActivoTrueAndFechaHoraBetweenOrderByFechaHoraDesc(tenantId, trabajadorId, desde, hasta)
                .stream()
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    public List<MarcaAsistenciaResponse> listarPorPeriodo(String trabajadorId, LocalDate fechaInicio, LocalDate fechaFin) {
        String tenantId = tenantContext.require().tenantId();
        Instant desde = fechaInicio.atStartOfDay(ZONA).toInstant();
        Instant hasta = fechaFin.plusDays(1).atStartOfDay(ZONA).toInstant();
        return marcaAsistenciaRepository
                .findByTenantIdAndTrabajadorIdAndActivoTrueAndFechaHoraBetweenOrderByFechaHoraDesc(tenantId, trabajadorId, desde, hasta)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MarcaAsistenciaResponse registrar(RegistrarMarcaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String authority = Roles.authorityFromClaim(actor.role());
        if (Roles.TRABAJADOR.equals(authority) && !request.trabajadorId().equals(actor.trabajadorId())) {
            throw new DomainException(403, "Un trabajador solo registra su propia marca");
        }
        MarcaAsistencia marca = new MarcaAsistencia();
        marca.setId(UUID.randomUUID().toString());
        marca.setTenantId(actor.tenantId());
        marca.setTrabajadorId(request.trabajadorId());
        marca.setTipo(request.tipo());
        marca.setFechaHora(Instant.now());
        marca.setOrigen(request.origen().trim());
        marca.setActivo(true);
        marca.setCreadoEn(Instant.now());
        return toResponse(marcaAsistenciaRepository.save(marca));
    }

    @Transactional
    public MarcaAsistenciaResponse editar(String marcaId, EditarMarcaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String authority = Roles.authorityFromClaim(actor.role());
        if (!Roles.ADMIN_RRHH.equals(authority) && !Roles.SUPERADMIN.equals(authority)) {
            throw new DomainException(403, "Solo Admin de RRHH edita marcas");
        }
        MarcaAsistencia marca = marcaAsistenciaRepository.findByIdAndTenantIdAndActivoTrue(marcaId, actor.tenantId())
                .orElseThrow(() -> new DomainException(404, "Marca no encontrada"));
        marca.setFechaHora(request.fechaHora());
        marca.setMotivoEdicion(request.motivo().trim());
        return toResponse(marcaAsistenciaRepository.save(marca));
    }

    public AsistenciaResumenResponse resumen(LocalDate fechaInicio, LocalDate fechaFin) {
        String tenantId = tenantContext.require().tenantId();
        Instant desde = fechaInicio.atStartOfDay(ZONA).toInstant();
        Instant hasta = fechaFin.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<MarcaAsistencia> marcas = marcaAsistenciaRepository.findByTenantIdAndActivoTrueAndFechaHoraBetween(tenantId, desde, hasta);
        long entradas = marcas.stream().filter(m -> m.getTipo() == TipoMarca.ENTRADA).count();
        long salidas = marcas.stream().filter(m -> m.getTipo() == TipoMarca.SALIDA).count();
        double porcentaje = marcas.isEmpty() ? 0 : (entradas * 100.0) / marcas.size();
        return new AsistenciaResumenResponse(fechaInicio, marcas.size(), entradas, salidas, porcentaje);
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
