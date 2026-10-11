package com.rrhh.asistencia.dto.response;

import com.rrhh.asistencia.model.EstadoHoraExtra;
import com.rrhh.asistencia.model.TipoHoraExtra;
import java.math.BigDecimal;
import java.time.LocalDate;

public record HoraExtraResponse(String id, String trabajadorId, LocalDate fecha, String periodo, TipoHoraExtra tipo,
                                BigDecimal cantidadHoras, BigDecimal valorHora, BigDecimal recargoPorcentaje,
                                BigDecimal montoTotal, String motivo, EstadoHoraExtra estado) {}
