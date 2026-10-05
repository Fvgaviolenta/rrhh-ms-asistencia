package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.dto.ApiResponse;
import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.dto.request.RegistrarMarcaRequest;
import com.rrhh.asistencia.dto.response.AsistenciaResumenResponse;
import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.service.MarcaAsistenciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MarcaAsistenciaController {
    private final MarcaAsistenciaService marcaAsistenciaService;

    public MarcaAsistenciaController(MarcaAsistenciaService marcaAsistenciaService) {
        this.marcaAsistenciaService = marcaAsistenciaService;
    }

    @GetMapping("/marcas-asistencia")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MarcaAsistenciaResponse>> listar() {
        return ApiResponse.ok(marcaAsistenciaService.listarPorTenant(), "Marcas de asistencia del tenant");
    }

    @GetMapping("/asistencia/trabajador/{trabajador_id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MarcaAsistenciaResponse>> porTrabajador(@PathVariable("trabajador_id") String trabajadorId) {
        return ApiResponse.ok(marcaAsistenciaService.listarPorTrabajador(trabajadorId), "Marcas del trabajador");
    }

    @GetMapping("/asistencia/trabajador/{trabajador_id}/hoy")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<MarcaAsistenciaResponse> hoy(@PathVariable("trabajador_id") String trabajadorId) {
        return ApiResponse.ok(marcaAsistenciaService.marcaDeHoy(trabajadorId), "Marca de hoy");
    }

    @GetMapping("/asistencia/trabajador/{trabajador_id}/periodo")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MarcaAsistenciaResponse>> periodo(
            @PathVariable("trabajador_id") String trabajadorId,
            @RequestParam("fecha_inicio") LocalDate fechaInicio,
            @RequestParam("fecha_fin") LocalDate fechaFin
    ) {
        return ApiResponse.ok(marcaAsistenciaService.listarPorPeriodo(trabajadorId, fechaInicio, fechaFin), "Marcas del periodo");
    }

    @PostMapping("/asistencia/registro")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MarcaAsistenciaResponse>> registrar(@Valid @RequestBody RegistrarMarcaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(marcaAsistenciaService.registrar(request), "Marca registrada"));
    }

    @PutMapping("/asistencia/{marca_id}/editar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<MarcaAsistenciaResponse> editar(
            @PathVariable("marca_id") String marcaId,
            @Valid @RequestBody EditarMarcaRequest request
    ) {
        return ApiResponse.ok(marcaAsistenciaService.editar(marcaId, request), "Marca editada");
    }

    @GetMapping("/asistencia/resumen")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','JEFATURA')")
    public ApiResponse<AsistenciaResumenResponse> resumen(
            @RequestParam("fecha_inicio") LocalDate fechaInicio,
            @RequestParam("fecha_fin") LocalDate fechaFin
    ) {
        return ApiResponse.ok(marcaAsistenciaService.resumen(fechaInicio, fechaFin), "Resumen de asistencia");
    }

    @GetMapping("/asistencia/status")
    public Map<String, String> status() {
        return Map.of(
                "servicio", "rrhh-asistencia",
                "estado", "SCAFFOLD"
        );
    }
}
