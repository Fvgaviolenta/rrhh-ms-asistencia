package com.rrhh.asistencia.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * Registro técnico de un request terminado, que va al stream rrhh-api-telemetry (fase 5 de streaming).
 * No lleva datos personales: ni cuerpo, query params, headers, token, userId, email ni trabajadorId;
 * la ruta es la plantilla de Spring MVC, nunca la URI real.
 */
public record RegistroTelemetria(String tipo, String servicio, String metodo, String ruta, int status,
                                 long latenciaMs, String tenantId, String ocurridoEn) {

    public static final String TIPO = "api.request";
    public static final String SERVICIO = "asistencia";

    // Propio y no el de la aplicación: ese es SNAKE_CASE y omite nulos, y rrhh-stream-metrics espera
    // latenciaMs y tenantId (null presente cuando el request no se autenticó).
    private static final ObjectMapper JSON = new ObjectMapper();

    public static RegistroTelemetria de(String metodo, String ruta, int status, long latenciaMs, String tenantId,
                                        Instant ocurridoEn) {
        return new RegistroTelemetria(TIPO, SERVICIO, metodo, ruta, status, latenciaMs, tenantId, ocurridoEn.toString());
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
