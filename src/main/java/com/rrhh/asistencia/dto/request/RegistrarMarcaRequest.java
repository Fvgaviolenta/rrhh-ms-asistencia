package com.rrhh.asistencia.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrarMarcaRequest(
        @NotBlank(message = "El trabajador_id es obligatorio")
        String trabajadorId,
        
        @NotNull(message = "El tipo de marca es obligatorio")
        TipoMarcaEnum tipo,
        
        @NotBlank(message = "El origen es obligatorio")
        String origen
) {
    public enum TipoMarcaEnum {
        ENTRADA,
        SALIDA
    }
}
