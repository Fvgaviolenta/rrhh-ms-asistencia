package com.rrhh.asistencia.dto.response;

import java.time.LocalDate;

public record AsistenciaResumenResponse(
        LocalDate fecha,
        long totalMarcas,
        long totalEntradas,
        long totalSalidas,
        double porcentajeAsistencia
) {
}
