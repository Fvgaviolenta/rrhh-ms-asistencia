package com.rrhh.asistencia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record CrearTurnoRequest(
        @NotBlank String nombre,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin
) {
}
