package com.rrhh.asistencia.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record EditarMarcaRequest(
        @NotNull(message = "La fecha y hora es obligatoria")
        Instant fechaHora,
        
        String motivo
) {
}
