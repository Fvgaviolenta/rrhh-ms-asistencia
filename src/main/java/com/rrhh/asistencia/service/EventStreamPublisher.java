package com.rrhh.asistencia.service;

import java.util.List;
import java.util.Map;

/**
 * Envía un lote de eventos al stream (Kinesis en AWS, Redis Streams en local).
 * Devuelve, por eventId, el error de los eventos que no se pudieron escribir; un mapa vacío es éxito total.
 * Si falla el lote completo, lanza la excepción y el outbox reintenta todos.
 */
public interface EventStreamPublisher {
    Map<String, String> publish(List<StreamEvent> events);
}
