package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.dto.ApiResponse;
import com.rrhh.asistencia.dto.request.CrearHoraExtraRequest;
import com.rrhh.asistencia.dto.response.HoraExtraResponse;
import com.rrhh.asistencia.dto.response.ResumenHorasExtraResponse;
import com.rrhh.asistencia.service.HoraExtraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/horas-extra")
public class HoraExtraController {
    private final HoraExtraService service;
    public HoraExtraController(HoraExtraService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<HoraExtraResponse>> crear(@Valid @RequestBody CrearHoraExtraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.crear(request), "Hora extra registrada"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<List<HoraExtraResponse>> listar(@RequestParam String periodo, @RequestParam(required = false, name = "trabajador_id") String trabajadorId) {
        return ApiResponse.ok(service.listar(trabajadorId, periodo), "Horas extra del período");
    }

    @PatchMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<HoraExtraResponse> aprobar(@PathVariable String id) { return ApiResponse.ok(service.aprobar(id, true), "Hora extra aprobada"); }

    @PatchMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<HoraExtraResponse> rechazar(@PathVariable String id) { return ApiResponse.ok(service.aprobar(id, false), "Hora extra rechazada"); }

    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<ResumenHorasExtraResponse> resumen(@RequestParam("trabajador_id") String trabajadorId, @RequestParam String periodo) {
        return ApiResponse.ok(service.resumenAprobado(trabajadorId, periodo), "Total aprobado de horas extra");
    }
}
