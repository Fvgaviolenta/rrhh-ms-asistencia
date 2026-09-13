package com.rrhh.asistencia.dto.response;

import com.rrhh.asistencia.model.TipoMarca;

import java.time.Instant;

public record MarcaAsistenciaResponse(
        String id,
        String tenantId,
        String trabajadorId,
        TipoMarca tipo,
        Instant fechaHora,
        String origen,
        boolean activo,
        Instant creadoEn
) {}
