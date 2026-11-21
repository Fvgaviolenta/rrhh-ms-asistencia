package com.rrhh.asistencia.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RedisEventStreamPublisherTest {
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> ops = mock(StreamOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisEventStreamPublisher publisher = new RedisEventStreamPublisher(redis, "rrhh:events", 10000);

    @Test
    @SuppressWarnings("unchecked")
    void agregaCadaEventoAlStreamYRecortaConMaxLenAproximado() {
        when(redis.opsForStream()).thenReturn((StreamOperations) ops);
        when(ops.add(any(MapRecord.class))).thenReturn(RecordId.autoGenerate());

        Map<String, String> fallidos = publisher.publish(List.of(
                new StreamEvent("e1", "asistencia.marca.registrada", "tenant-a", "{\"eventId\":\"e1\"}")));

        ArgumentCaptor<MapRecord<String, String, String>> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(ops).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo("rrhh:events");
        assertThat(captor.getValue().getValue())
                .containsEntry("eventId", "e1")
                .containsEntry("eventType", "asistencia.marca.registrada")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("event", "{\"eventId\":\"e1\"}");
        verify(ops).trim("rrhh:events", 10000, true);
        assertThat(fallidos).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void unEventoQueFallaQuedaEnLaListaDeFallidos() {
        when(redis.opsForStream()).thenReturn((StreamOperations) ops);
        when(ops.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis caido"));

        Map<String, String> fallidos = publisher.publish(List.of(new StreamEvent("e1", "x", "t", "{}")));

        assertThat(fallidos).containsEntry("e1", "redis caido");
        verify(ops, never()).trim(any(), anyLong(), anyBoolean());
    }
}
