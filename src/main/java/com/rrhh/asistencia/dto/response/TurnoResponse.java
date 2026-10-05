package com.rrhh.asistencia.dto.response;

import java.time.LocalTime;

public record TurnoResponse(
        String id,
        String tenantId,
        String nombre,
        LocalTime horaInicio,
        LocalTime horaFin,
        boolean activo
) {
}
