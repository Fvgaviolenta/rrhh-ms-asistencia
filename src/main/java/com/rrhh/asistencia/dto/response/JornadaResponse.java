package com.rrhh.asistencia.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;

public record JornadaResponse(
        String id,
        String tenantId,
        String trabajadorId,
        String turnoId,
        String turnoNombre,
        LocalTime horaInicio,
        LocalTime horaFin,
        LocalDate fecha,
        boolean activo
) {
}
