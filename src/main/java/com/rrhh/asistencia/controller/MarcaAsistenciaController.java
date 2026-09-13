package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.dto.ApiResponse;
import com.rrhh.asistencia.dto.response.MarcaAsistenciaResponse;
import com.rrhh.asistencia.service.MarcaAsistenciaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/asistencia/status")
    public Map<String, String> status() {
        return Map.of(
                "servicio", "rrhh-asistencia",
                "estado", "SCAFFOLD"
        );
    }
}
