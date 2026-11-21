package com.rrhh.asistencia.service;

/**
 * Un evento listo para el stream: {@code json} es el sobre completo (eventId, eventType, tenantId,
 * occurredAt, aggregateId, payload), el mismo formato que usan los productores SQS.
 */
public record StreamEvent(String eventId, String eventType, String tenantId, String json) {
}
