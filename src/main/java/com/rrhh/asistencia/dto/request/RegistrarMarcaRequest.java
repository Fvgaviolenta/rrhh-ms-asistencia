package com.rrhh.asistencia.dto.request;

import com.rrhh.asistencia.model.TipoMarca;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrarMarcaRequest(
        @NotBlank String trabajadorId,
        @NotNull TipoMarca tipo,
        @NotBlank String origen
) {
}
