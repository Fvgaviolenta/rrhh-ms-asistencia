package com.rrhh.asistencia.service;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequestEntry;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PutRecords con partition key = tenantId: los eventos de una empresa caen en el mismo shard y en orden. */
public class KinesisEventStreamPublisher implements EventStreamPublisher {
    private final KinesisClient client;
    private final String streamName;

    public KinesisEventStreamPublisher(KinesisClient client, String streamName) {
        if (streamName == null || streamName.isBlank()) {
            throw new IllegalStateException("RRHH_KINESIS_ENABLED=true requiere KINESIS_STREAM_NAME");
        }
        this.client = client;
        this.streamName = streamName;
    }

    @Override
    public Map<String, String> publish(List<StreamEvent> events) {
        if (events.isEmpty()) return Map.of();
        List<PutRecordsRequestEntry> entries = events.stream()
                .map(e -> PutRecordsRequestEntry.builder()
                        .partitionKey(e.tenantId())
                        .data(SdkBytes.fromUtf8String(e.json()))
                        .build())
                .toList();
        PutRecordsResponse response = client.putRecords(PutRecordsRequest.builder()
                .streamName(streamName)
                .records(entries)
                .build());
        Map<String, String> fallidos = new LinkedHashMap<>();
        if (response.failedRecordCount() == null || response.failedRecordCount() == 0) return fallidos;
        // Kinesis responde un resultado por registro, en el mismo orden del request.
        List<PutRecordsResultEntry> resultados = response.records();
        for (int i = 0; i < events.size(); i++) {
            PutRecordsResultEntry r = i < resultados.size() ? resultados.get(i) : null;
            if (r == null) {
                fallidos.put(events.get(i).eventId(), "Kinesis no devolvió resultado para el registro");
            } else if (r.errorCode() != null) {
                fallidos.put(events.get(i).eventId(), r.errorCode() + ": " + r.errorMessage());
            }
        }
        return fallidos;
    }
}
