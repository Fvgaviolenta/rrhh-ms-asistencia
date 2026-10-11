package com.rrhh.asistencia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CrearJornadaRequest(
        @NotBlank String trabajadorId,
        @NotBlank String turnoId,
        @NotNull LocalDate fecha
) {
}
