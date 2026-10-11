package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.dto.ApiResponse;
import com.rrhh.asistencia.dto.request.CrearJornadaRequest;
import com.rrhh.asistencia.dto.request.CrearTurnoRequest;
import com.rrhh.asistencia.dto.response.JornadaResponse;
import com.rrhh.asistencia.dto.response.TurnoResponse;
import com.rrhh.asistencia.service.HorarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HorarioController {
    private final HorarioService horarioService;

    public HorarioController(HorarioService horarioService) {
        this.horarioService = horarioService;
    }

    @GetMapping({"/turnos", "/asistencia/turnos"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','JEFATURA')")
    public ApiResponse<List<TurnoResponse>> listarTurnos() {
        return ApiResponse.ok(horarioService.listarTurnos(), "Turnos del tenant");
    }

    @PostMapping({"/turnos", "/asistencia/turnos"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<TurnoResponse>> crearTurno(@Valid @RequestBody CrearTurnoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(horarioService.crearTurno(request), "Turno creado"));
    }

    @GetMapping({"/jornadas", "/asistencia/jornadas"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','JEFATURA')")
    public ApiResponse<List<JornadaResponse>> listarJornadas() {
        return ApiResponse.ok(horarioService.listarJornadas(), "Jornadas del tenant");
    }

    @GetMapping({"/jornadas/mias", "/asistencia/jornadas/mias"})
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<JornadaResponse>> listarMias() {
        return ApiResponse.ok(horarioService.listarMias(), "Jornadas del trabajador");
    }

    @PostMapping({"/jornadas", "/asistencia/jornadas"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<JornadaResponse>> asignar(@Valid @RequestBody CrearJornadaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(horarioService.asignar(request), "Jornada asignada"));
    }

    @DeleteMapping({"/jornadas/{jornada_id}", "/asistencia/jornadas/{jornada_id}"})
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<Map<String, Boolean>> quitar(@PathVariable("jornada_id") String jornadaId) {
        horarioService.quitar(jornadaId);
        return ApiResponse.ok(Map.of("activo", false), "Jornada desasignada");
    }
}
