package com.rrhh.asistencia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.asistencia.model.OutboxEvent;
import com.rrhh.asistencia.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final EventStreamPublisher stream = mock(EventStreamPublisher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<EventStreamPublisher> provider = mock(ObjectProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxPublisher outbox = new OutboxPublisher(repository, objectMapper, provider);

    private static OutboxEvent pendiente(String id, String tenant) {
        OutboxEvent e = new OutboxEvent();
        e.setId(id);
        e.setTenantId(tenant);
        e.setEventType("asistencia.marca.registrada");
        e.setAggregateId("marca-" + id);
        e.setPayload("{\"trabajadorId\":\"w1\",\"turnoHoraInicio\":\"08:00\"}");
        e.setEstado(OutboxEvent.PENDIENTE);
        e.setCreadoEn(Instant.parse("2026-11-03T11:07:42Z"));
        return e;
    }

    @Test
    void registrarGuardaElEventoPendienteConElTenantRecibido() {
        outbox.registrar("asistencia.marca.registrada", "tenant-a", "marca-1", Map.of("trabajadorId", "w1"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getEstado()).isEqualTo(OutboxEvent.PENDIENTE);
        assertThat(captor.getValue().getPayload()).isEqualTo("{\"trabajadorId\":\"w1\"}");
    }

    @Test
    void sinPublicadorConfiguradoNoLeeElOutbox() {
        when(provider.getIfAvailable()).thenReturn(null);

        outbox.publicarPendientes();

        verifyNoInteractions(repository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicaElSobreYMarcaComoPublicado() throws Exception {
        OutboxEvent e1 = pendiente("e1", "tenant-a");
        when(provider.getIfAvailable()).thenReturn(stream);
        when(repository.findTop20ByEstadoOrderByCreadoEnAsc(OutboxEvent.PENDIENTE)).thenReturn(List.of(e1));
        when(stream.publish(anyList())).thenReturn(Map.of());

        outbox.publicarPendientes();

        ArgumentCaptor<List<StreamEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(stream).publish(captor.capture());
        StreamEvent enviado = captor.getValue().get(0);
        assertThat(enviado.tenantId()).isEqualTo("tenant-a");
        JsonNode sobre = objectMapper.readTree(enviado.json());
        assertThat(sobre.path("eventId").asText()).isEqualTo("e1");
        assertThat(sobre.path("eventType").asText()).isEqualTo("asistencia.marca.registrada");
        assertThat(sobre.path("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(sobre.path("occurredAt").asText()).isEqualTo("2026-11-03T11:07:42Z");
        assertThat(sobre.path("aggregateId").asText()).isEqualTo("marca-e1");
        assertThat(sobre.path("payload").path("turnoHoraInicio").asText()).isEqualTo("08:00");
        assertThat(e1.getEstado()).isEqualTo(OutboxEvent.PUBLICADO);
        assertThat(e1.getPublicadoEn()).isNotNull();
    }

    @Test
    void unRegistroRechazadoQuedaPendienteYLosDemasSePublican() {
        OutboxEvent e1 = pendiente("e1", "t");
        OutboxEvent e2 = pendiente("e2", "t");
        when(provider.getIfAvailable()).thenReturn(stream);
        when(repository.findTop20ByEstadoOrderByCreadoEnAsc(OutboxEvent.PENDIENTE)).thenReturn(List.of(e1, e2));
        when(stream.publish(anyList())).thenReturn(Map.of("e2", "ProvisionedThroughputExceededException: Rate exceeded"));

        outbox.publicarPendientes();

        assertThat(e1.getEstado()).isEqualTo(OutboxEvent.PUBLICADO);
        assertThat(e2.getEstado()).isEqualTo(OutboxEvent.PENDIENTE);
        assertThat(e2.getIntentos()).isEqualTo(1);
        assertThat(e2.getUltimoError()).contains("ProvisionedThroughputExceededException");
    }

    @Test
    void siFallaElLoteCompletoTodosQuedanPendientesConUnIntentoMas() {
        OutboxEvent e1 = pendiente("e1", "t");
        OutboxEvent e2 = pendiente("e2", "t");
        when(provider.getIfAvailable()).thenReturn(stream);
        when(repository.findTop20ByEstadoOrderByCreadoEnAsc(OutboxEvent.PENDIENTE)).thenReturn(List.of(e1, e2));
        when(stream.publish(anyList())).thenThrow(new IllegalStateException("sin red"));

        outbox.publicarPendientes();

        assertThat(List.of(e1, e2)).allSatisfy(e -> {
            assertThat(e.getEstado()).isEqualTo(OutboxEvent.PENDIENTE);
            assertThat(e.getIntentos()).isEqualTo(1);
            assertThat(e.getUltimoError()).isEqualTo("sin red");
        });
    }
}
