package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.CrearHoraExtraRequest;
import com.rrhh.asistencia.dto.response.HoraExtraResponse;
import com.rrhh.asistencia.dto.response.ResumenHorasExtraResponse;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.*;
import com.rrhh.asistencia.repository.HoraExtraRepository;
import com.rrhh.asistencia.security.Roles;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class HoraExtraService {
    private final HoraExtraRepository repository;
    private final TenantContext tenantContext;

    public HoraExtraService(HoraExtraRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public HoraExtraResponse crear(CrearHoraExtraRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirGestion(actor);
        HoraExtra hora = new HoraExtra();
        hora.setId(UUID.randomUUID().toString());
        hora.setTenantId(actor.tenantId());
        hora.setTrabajadorId(request.trabajadorId());
        hora.setFecha(request.fecha());
        hora.setPeriodo(String.format("%04d-%02d", request.fecha().getYear(), request.fecha().getMonthValue()));
        hora.setTipo(request.tipo());
        hora.setCantidadHoras(request.cantidadHoras());
        hora.setValorHora(request.valorHora());
        BigDecimal recargo = request.recargoPorcentaje() == null ? BigDecimal.ZERO : request.recargoPorcentaje();
        hora.setRecargoPorcentaje(recargo);
        hora.setMontoTotal(request.cantidadHoras().multiply(request.valorHora())
                .multiply(BigDecimal.ONE.add(recargo.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP));
        hora.setMotivo(request.motivo().trim());
        hora.setEstado(EstadoHoraExtra.PENDIENTE);
        hora.setCreadoPor(actor.userId());
        hora.setCreadoEn(Instant.now());
        return toResponse(repository.save(hora));
    }

    public List<HoraExtraResponse> listar(String trabajadorId, String periodo) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirGestion(actor);
        return (trabajadorId == null || trabajadorId.isBlank()
                ? repository.findByTenantIdAndPeriodoOrderByFechaDesc(actor.tenantId(), periodo)
                : repository.findByTenantIdAndTrabajadorIdAndPeriodoOrderByFechaDesc(actor.tenantId(), trabajadorId, periodo))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public HoraExtraResponse aprobar(String id, boolean aprobada) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirGestion(actor);
        HoraExtra hora = buscar(id, actor.tenantId());
        if (hora.getEstado() != EstadoHoraExtra.PENDIENTE) throw new DomainException(409, "Solo se pueden resolver registros pendientes");
        hora.setEstado(aprobada ? EstadoHoraExtra.APROBADA : EstadoHoraExtra.RECHAZADA);
        hora.setAprobadoPor(actor.userId());
        hora.setAprobadoEn(Instant.now());
        return toResponse(repository.save(hora));
    }

    public ResumenHorasExtraResponse resumenAprobado(String trabajadorId, String periodo) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        exigirGestion(actor);
        List<HoraExtra> horas = repository.findByTenantIdAndTrabajadorIdAndPeriodoAndEstado(actor.tenantId(), trabajadorId, periodo, EstadoHoraExtra.APROBADA);
        BigDecimal total = horas.stream().map(HoraExtra::getMontoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ResumenHorasExtraResponse(trabajadorId, periodo, total, horas.stream().map(HoraExtra::getId).toList());
    }

    private HoraExtra buscar(String id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new DomainException(404, "Hora extra no encontrada"));
    }

    private void exigirGestion(TenantContext.AuthenticatedUser actor) {
        String role = Roles.authorityFromClaim(actor.role());
        if (!Roles.ADMIN_RRHH.equals(role) && !Roles.SUPERADMIN.equals(role)) throw new DomainException(403, "No tiene permisos para gestionar horas extra");
    }

    private HoraExtraResponse toResponse(HoraExtra h) {
        return new HoraExtraResponse(h.getId(), h.getTrabajadorId(), h.getFecha(), h.getPeriodo(), h.getTipo(), h.getCantidadHoras(), h.getValorHora(), h.getRecargoPorcentaje(), h.getMontoTotal(), h.getMotivo(), h.getEstado());
    }
}
