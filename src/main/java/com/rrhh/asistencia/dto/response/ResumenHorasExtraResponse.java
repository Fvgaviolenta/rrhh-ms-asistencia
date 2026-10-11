package com.rrhh.asistencia.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ResumenHorasExtraResponse(String trabajadorId, String periodo, BigDecimal montoTotal, List<String> registrosIds) {}
