package com.rrhh.asistencia.service;

import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Respaldo local de Kinesis: XADD a un Redis Stream y recorte aproximado (MAXLEN ~) para que no crezca sin límite.
 * Para ver el flujo: {@code docker exec rrhh-redis redis-cli XRANGE rrhh:events - +}.
 */
public class RedisEventStreamPublisher implements EventStreamPublisher {
    private final StringRedisTemplate redis;
    private final String streamKey;
    private final long maxLen;

    public RedisEventStreamPublisher(StringRedisTemplate redis, String streamKey, long maxLen) {
        this.redis = redis;
        this.streamKey = streamKey;
        this.maxLen = maxLen;
    }

    @Override
    public Map<String, String> publish(List<StreamEvent> events) {
        Map<String, String> fallidos = new LinkedHashMap<>();
        for (StreamEvent e : events) {
            try {
                Map<String, String> campos = new LinkedHashMap<>();
                campos.put("eventId", e.eventId());
                campos.put("eventType", e.eventType());
                campos.put("tenantId", e.tenantId());
                campos.put("event", e.json());
                redis.opsForStream().add(StreamRecords.string(campos).withStreamKey(streamKey));
            } catch (RuntimeException ex) {
                fallidos.put(e.eventId(), ex.getMessage());
            }
        }
        if (fallidos.size() < events.size()) {
            redis.opsForStream().trim(streamKey, maxLen, true);
        }
        return fallidos;
    }
}
