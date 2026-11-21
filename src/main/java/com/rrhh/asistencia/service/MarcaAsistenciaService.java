package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.dto.request.RegistrarMarcaRequest;
import com.rrhh.asistencia.dto.response.AsistenciaResumenResponse;
import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.MarcaAsistencia;
import com.rrhh.asistencia.model.TipoMarca;
import com.rrhh.asistencia.repository.JornadaRepository;
import com.rrhh.asistencia.repository.MarcaAsistenciaRepository;
import com.rrhh.asistencia.repository.TurnoRepository;
import com.rrhh.asistencia.security.Roles;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MarcaAsistenciaService {
    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    static final String EVENTO_MARCA_REGISTRADA = "asistencia.marca.registrada";
    static final String EVENTO_MARCA_EDITADA = "asistencia.editada";

    private final MarcaAsistenciaRepository marcaAsistenciaRepository;
    private final JornadaRepository jornadaRepository;
    private final TurnoRepository turnoRepository;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    public MarcaAsistenciaService(MarcaAsistenciaRepository marcaAsistenciaRepository, JornadaRepository jornadaRepository,
                                  TurnoRepository turnoRepository, OutboxPublisher outboxPublisher, TenantContext tenantContext) {
        this.marcaAsistenciaRepository = marcaAsistenciaRepository;
        this.jornadaRepository = jornadaRepository;
        this.turnoRepository = turnoRepository;
        this.outboxPublisher = outboxPublisher;
        this.tenantContext = tenantContext;
    }

    public List<MarcaAsistenciaResponse> listarPorTenant() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirRolDeGestion(actor);
        String tenantId = actor.tenantId();
        return marcaAsistenciaRepository.findByTenantIdAndActivoTrueOrderByFechaHoraDesc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<MarcaAsistenciaResponse> listarPorTrabajador(String trabajadorId) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirAccesoATrabajador(actor, trabajadorId);
        String tenantId = actor.tenantId();
        return marcaAsistenciaRepository
                .findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(tenantId, trabajadorId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MarcaAsistenciaResponse marcaDeHoy(String trabajadorId) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirAccesoATrabajador(actor, trabajadorId);
        String tenantId = actor.tenantId();
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
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirAccesoATrabajador(actor, trabajadorId);
        String tenantId = actor.tenantId();
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
        if (Roles.TRABAJADOR.equals(authority)) {
            exigirTrabajadorVinculado(actor);
            if (!request.trabajadorId().equals(actor.trabajadorId())) {
                throw new DomainException(403, "Un trabajador solo registra su propia asistencia");
            }
            LocalDate hoy = LocalDate.now(ZONA);
            Instant desde = hoy.atStartOfDay(ZONA).toInstant();
            Instant hasta = hoy.plusDays(1).atStartOfDay(ZONA).toInstant();
            if (marcaAsistenciaRepository.existsByTenantIdAndTrabajadorIdAndActivoTrueAndFechaHoraBetween(
                    actor.tenantId(), actor.trabajadorId(), desde, hasta)) {
                throw new DomainException(409, "Ya registraste tu asistencia de hoy");
            }
        }
        MarcaAsistencia marca = new MarcaAsistencia();
        marca.setId(UUID.randomUUID().toString());
        marca.setTenantId(actor.tenantId());
        marca.setTrabajadorId(request.trabajadorId());
        marca.setTipo(Roles.TRABAJADOR.equals(authority) ? TipoMarca.ENTRADA : request.tipo());
        marca.setFechaHora(Instant.now());
        marca.setOrigen(request.origen().trim());
        marca.setActivo(true);
        marca.setCreadoEn(Instant.now());
        MarcaAsistencia guardada = marcaAsistenciaRepository.save(marca);
        outboxPublisher.registrar(EVENTO_MARCA_REGISTRADA, guardada.getTenantId(), guardada.getId(), payloadDeMarca(guardada));
        return toResponse(guardada);
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
        Instant fechaHoraAnterior = marca.getFechaHora();
        marca.setFechaHora(request.fechaHora());
        marca.setMotivoEdicion(request.motivo().trim());
        MarcaAsistencia guardada = marcaAsistenciaRepository.save(marca);
        // El motivo es texto libre y puede traer datos personales: no viaja por el stream.
        Map<String, Object> payload = payloadDeMarca(guardada);
        payload.put("fechaHoraAnterior", fechaHoraAnterior.toString());
        outboxPublisher.registrar(EVENTO_MARCA_EDITADA, guardada.getTenantId(), guardada.getId(), payload);
        return toResponse(guardada);
    }

    public AsistenciaResumenResponse resumen(LocalDate fechaInicio, LocalDate fechaFin) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirRolDeGestion(actor);
        String tenantId = actor.tenantId();
        Instant desde = fechaInicio.atStartOfDay(ZONA).toInstant();
        Instant hasta = fechaFin.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<MarcaAsistencia> marcas = marcaAsistenciaRepository.findByTenantIdAndActivoTrueAndFechaHoraBetween(tenantId, desde, hasta);
        long entradas = marcas.stream().filter(m -> m.getTipo() == TipoMarca.ENTRADA).count();
        long salidas = marcas.stream().filter(m -> m.getTipo() == TipoMarca.SALIDA).count();
        double porcentaje = marcas.isEmpty() ? 0 : (entradas * 100.0) / marcas.size();
        return new AsistenciaResumenResponse(fechaInicio, marcas.size(), entradas, salidas, porcentaje);
    }

    /**
     * Payload del evento, solo con identificadores (ADR-019): sin nombres, RUT ni salarios. Lleva la hora de inicio
     * del turno de la jornada de ese día, si existe, para que el detector de atrasos decida sin consultar MySQL.
     */
    private Map<String, Object> payloadDeMarca(MarcaAsistencia marca) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("trabajadorId", marca.getTrabajadorId());
        payload.put("tipo", marca.getTipo().name());
        payload.put("fechaHora", marca.getFechaHora().toString());
        payload.put("origen", marca.getOrigen());
        LocalDate fecha = LocalDate.ofInstant(marca.getFechaHora(), ZONA);
        jornadaRepository.findFirstByTenantIdAndTrabajadorIdAndFechaAndActivoTrue(marca.getTenantId(), marca.getTrabajadorId(), fecha)
                .flatMap(jornada -> turnoRepository.findByIdAndTenantId(jornada.getTurnoId(), marca.getTenantId()))
                .ifPresent(turno -> payload.put("turnoHoraInicio", turno.getHoraInicio().format(HORA)));
        payload.put("zona", ZONA.getId());
        return payload;
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

    private void exigirAccesoATrabajador(TenantContext.AuthenticatedUser actor, String trabajadorId) {
        if (Roles.TRABAJADOR.equals(Roles.authorityFromClaim(actor.role()))) {
            exigirTrabajadorVinculado(actor);
            if (!actor.trabajadorId().equals(trabajadorId)) {
                throw new DomainException(403, "Un trabajador solo puede consultar su propia asistencia");
            }
        }
    }

    private void exigirTrabajadorVinculado(TenantContext.AuthenticatedUser actor) {
        if (actor.trabajadorId() == null || actor.trabajadorId().isBlank()) {
            throw new DomainException(403, "La cuenta no está vinculada a un trabajador");
        }
    }

    private void exigirRolDeGestion(TenantContext.AuthenticatedUser actor) {
        String authority = Roles.authorityFromClaim(actor.role());
        if (!Roles.ADMIN_RRHH.equals(authority) && !Roles.SUPERADMIN.equals(authority) && !Roles.JEFATURA.equals(authority)) {
            throw new DomainException(403, "No tienes permisos para consultar la asistencia del equipo");
        }
    }
}
