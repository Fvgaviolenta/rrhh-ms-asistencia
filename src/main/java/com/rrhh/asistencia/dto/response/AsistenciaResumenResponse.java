package com.rrhh.asistencia.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AsistenciaResumenResponse(
        LocalDate fecha,
        Long totalTrabajadores,
        Long totalMarcas,
        Long marcasEntrada,
        Long marcasSalida,
        BigDecimal porcentajeAsistencia
) {
}
