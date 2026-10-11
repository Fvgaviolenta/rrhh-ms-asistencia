package com.rrhh.asistencia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record EditarMarcaRequest(
        @NotNull Instant fechaHora,
        @NotBlank String motivo
) {
}
