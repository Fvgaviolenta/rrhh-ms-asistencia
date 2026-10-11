package com.rrhh.asistencia.dto.request;

import com.rrhh.asistencia.model.TipoHoraExtra;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearHoraExtraRequest(
        @NotBlank String trabajadorId,
        @NotNull LocalDate fecha,
        @NotNull TipoHoraExtra tipo,
        @NotNull @DecimalMin("0.01") BigDecimal cantidadHoras,
        @NotNull @DecimalMin("0.01") BigDecimal valorHora,
        @DecimalMin("0.00") BigDecimal recargoPorcentaje,
        @NotBlank @Size(max = 500) String motivo
) {}
