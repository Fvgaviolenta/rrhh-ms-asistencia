package com.rrhh.asistencia.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.asistencia.model.OutboxEvent;
import com.rrhh.asistencia.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox de asistencia (ADR-019), mismo patrón que ausencias y contratos: {@link #registrar} escribe el evento
 * en la transacción del cambio de negocio y {@link #publicarPendientes} lo envía después al stream.
 */
@Service
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<EventStreamPublisher> streamPublisher;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper,
                           ObjectProvider<EventStreamPublisher> streamPublisher) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.streamPublisher = streamPublisher;
    }

    @Transactional
    public void registrar(String eventType, String tenantId, String aggregateId, Map<String, Object> payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString());
            event.setTenantId(tenantId);
            event.setEventType(eventType);
            event.setAggregateId(aggregateId);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setEstado(OutboxEvent.PENDIENTE);
            event.setIntentos(0);
            event.setCreadoEn(Instant.now());
            repository.save(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No fue posible serializar el evento", ex);
        }
    }

    @Scheduled(fixedDelayString = "${rrhh.streaming.publish-delay-ms:5000}")
    @Transactional
    public void publicarPendientes() {
        EventStreamPublisher publisher = streamPublisher.getIfAvailable();
        if (publisher == null) return;
        List<OutboxEvent> pendientes = repository.findTop20ByEstadoOrderByCreadoEnAsc(OutboxEvent.PENDIENTE);
        if (pendientes.isEmpty()) return;

        Map<String, OutboxEvent> porId = new LinkedHashMap<>();
        List<StreamEvent> lote = new ArrayList<>();
        for (OutboxEvent event : pendientes) {
            try {
                lote.add(new StreamEvent(event.getId(), event.getEventType(), event.getTenantId(), sobre(event)));
                porId.put(event.getId(), event);
            } catch (JsonProcessingException ex) {
                fallo(event, "Payload ilegible: " + ex.getOriginalMessage());
            }
        }
        if (lote.isEmpty()) return;

        Map<String, String> fallidos;
        try {
            fallidos = publisher.publish(lote);
        } catch (RuntimeException ex) {
            log.warn("No se pudo publicar el lote de {} eventos al stream: {}", lote.size(), ex.getMessage());
            porId.values().forEach(e -> fallo(e, ex.getMessage()));
            return;
        }
        Instant ahora = Instant.now();
        porId.forEach((id, event) -> {
            String error = fallidos.get(id);
            if (error != null) {
                fallo(event, error);
            } else {
                event.setEstado(OutboxEvent.PUBLICADO);
                event.setPublicadoEn(ahora);
                event.setUltimoError(null);
            }
        });
        if (!fallidos.isEmpty()) {
            log.warn("{} de {} eventos quedaron pendientes para reintento", fallidos.size(), lote.size());
        }
    }

    /** Sobre común con SQS: eventId, eventType, tenantId, occurredAt, aggregateId y payload. */
    private String sobre(OutboxEvent event) throws JsonProcessingException {
        Map<String, Object> sobre = new LinkedHashMap<>();
        sobre.put("eventId", event.getId());
        sobre.put("eventType", event.getEventType());
        sobre.put("tenantId", event.getTenantId());
        sobre.put("occurredAt", event.getCreadoEn().toString());
        sobre.put("aggregateId", event.getAggregateId());
        sobre.put("payload", objectMapper.readTree(event.getPayload()));
        return objectMapper.writeValueAsString(sobre);
    }

    private void fallo(OutboxEvent event, String error) {
        event.setIntentos(event.getIntentos() + 1);
        event.setUltimoError(error);
    }
}
