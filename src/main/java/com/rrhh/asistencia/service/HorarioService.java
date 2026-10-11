package com.rrhh.asistencia.service;

import com.rrhh.asistencia.dto.request.CrearJornadaRequest;
import com.rrhh.asistencia.dto.request.CrearTurnoRequest;
import com.rrhh.asistencia.dto.response.JornadaResponse;
import com.rrhh.asistencia.dto.response.TurnoResponse;
import com.rrhh.asistencia.exception.DomainException;
import com.rrhh.asistencia.model.Jornada;
import com.rrhh.asistencia.model.Turno;
import com.rrhh.asistencia.repository.JornadaRepository;
import com.rrhh.asistencia.repository.TurnoRepository;
import com.rrhh.asistencia.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class HorarioService {
    private final TurnoRepository turnoRepository;
    private final JornadaRepository jornadaRepository;
    private final TenantContext tenantContext;

    public HorarioService(
            TurnoRepository turnoRepository,
            JornadaRepository jornadaRepository,
            TenantContext tenantContext
    ) {
        this.turnoRepository = turnoRepository;
        this.jornadaRepository = jornadaRepository;
        this.tenantContext = tenantContext;
    }

    public List<TurnoResponse> listarTurnos() {
        return turnoRepository.findByTenantIdAndActivoTrueOrderByNombreAsc(tenantContext.require().tenantId())
                .stream()
                .map(this::toTurno)
                .toList();
    }

    @Transactional
    public TurnoResponse crearTurno(CrearTurnoRequest request) {
        if (!request.horaFin().isAfter(request.horaInicio())) {
            throw new DomainException(400, "La hora de fin debe ser posterior a la de inicio", "hora_fin");
        }
        Turno turno = new Turno();
        turno.setId(UUID.randomUUID().toString());
        turno.setTenantId(tenantContext.require().tenantId());
        turno.setNombre(request.nombre().trim());
        turno.setHoraInicio(request.horaInicio());
        turno.setHoraFin(request.horaFin());
        turno.setActivo(true);
        return toTurno(turnoRepository.save(turno));
    }

    public List<JornadaResponse> listarJornadas() {
        String tenantId = tenantContext.require().tenantId();
        return jornadaRepository.findByTenantIdAndActivoTrueOrderByFechaAsc(tenantId).stream()
                .map(jornada -> toJornada(jornada, buscarTurno(jornada.getTurnoId(), tenantId)))
                .toList();
    }

    public List<JornadaResponse> listarMias() {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        if (actor.trabajadorId() == null || actor.trabajadorId().isBlank()) {
            throw new DomainException(403, "El token no incluye trabajador_id");
        }
        return jornadaRepository
                .findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaAsc(actor.tenantId(), actor.trabajadorId())
                .stream()
                .map(jornada -> toJornada(jornada, buscarTurno(jornada.getTurnoId(), actor.tenantId())))
                .toList();
    }

    @Transactional
    public JornadaResponse asignar(CrearJornadaRequest request) {
        String tenantId = tenantContext.require().tenantId();
        Turno turno = turnoRepository.findByIdAndTenantId(request.turnoId(), tenantId)
                .filter(Turno::isActivo)
                .orElseThrow(() -> new DomainException(404, "Turno no encontrado", "turno_id"));
        if (jornadaRepository.existsByTenantIdAndTrabajadorIdAndFechaAndActivoTrue(
                tenantId, request.trabajadorId(), request.fecha())) {
            throw new DomainException(400, "El trabajador ya tiene una jornada activa ese día", "fecha");
        }
        Jornada jornada = new Jornada();
        jornada.setId(UUID.randomUUID().toString());
        jornada.setTenantId(tenantId);
        jornada.setTrabajadorId(request.trabajadorId());
        jornada.setTurnoId(turno.getId());
        jornada.setFecha(request.fecha());
        jornada.setActivo(true);
        return toJornada(jornadaRepository.save(jornada), turno);
    }

    @Transactional
    public void quitar(String jornadaId) {
        String tenantId = tenantContext.require().tenantId();
        Jornada jornada = jornadaRepository.findByIdAndTenantId(jornadaId, tenantId)
                .orElseThrow(() -> new DomainException(404, "Jornada no encontrada"));
        jornada.setActivo(false);
        jornadaRepository.save(jornada);
    }

    private Turno buscarTurno(String turnoId, String tenantId) {
        return turnoRepository.findByIdAndTenantId(turnoId, tenantId)
                .orElseThrow(() -> new DomainException(404, "Turno no encontrado", "turno_id"));
    }

    private TurnoResponse toTurno(Turno turno) {
        return new TurnoResponse(
                turno.getId(), turno.getTenantId(), turno.getNombre(),
                turno.getHoraInicio(), turno.getHoraFin(), turno.isActivo()
        );
    }

    private JornadaResponse toJornada(Jornada jornada, Turno turno) {
        return new JornadaResponse(
                jornada.getId(),
                jornada.getTenantId(),
                jornada.getTrabajadorId(),
                jornada.getTurnoId(),
                turno.getNombre(),
                turno.getHoraInicio(),
                turno.getHoraFin(),
                jornada.getFecha(),
                jornada.isActivo()
        );
    }
}
