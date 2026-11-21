package com.rrhh.asistencia.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KinesisEventStreamPublisherTest {
    private final KinesisClient client = mock(KinesisClient.class);
    private final KinesisEventStreamPublisher publisher = new KinesisEventStreamPublisher(client, "rrhh-events");

    private static StreamEvent evento(String id, String tenant) {
        return new StreamEvent(id, "asistencia.marca.registrada", tenant, "{\"eventId\":\"" + id + "\"}");
    }

    @Test
    void enviaElLoteCompletoConPartitionKeyIgualAlTenant() {
        when(client.putRecords(any(PutRecordsRequest.class))).thenReturn(PutRecordsResponse.builder().failedRecordCount(0)
                .records(PutRecordsResultEntry.builder().sequenceNumber("1").shardId("s").build(),
                        PutRecordsResultEntry.builder().sequenceNumber("2").shardId("s").build()).build());

        Map<String, String> fallidos = publisher.publish(List.of(evento("e1", "tenant-a"), evento("e2", "tenant-b")));

        ArgumentCaptor<PutRecordsRequest> captor = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(client).putRecords(captor.capture());
        PutRecordsRequest request = captor.getValue();
        assertThat(request.streamName()).isEqualTo("rrhh-events");
        assertThat(request.records()).hasSize(2);
        assertThat(request.records().get(0).partitionKey()).isEqualTo("tenant-a");
        assertThat(request.records().get(1).partitionKey()).isEqualTo("tenant-b");
        assertThat(request.records().get(0).data().asUtf8String()).isEqualTo("{\"eventId\":\"e1\"}");
        assertThat(fallidos).isEmpty();
    }

    @Test
    void devuelveSoloLosRegistrosQueKinesisRechazo() {
        when(client.putRecords(any(PutRecordsRequest.class))).thenReturn(PutRecordsResponse.builder().failedRecordCount(1)
                .records(PutRecordsResultEntry.builder().sequenceNumber("1").shardId("s").build(),
                        PutRecordsResultEntry.builder().errorCode("ProvisionedThroughputExceededException")
                                .errorMessage("Rate exceeded").build()).build());

        Map<String, String> fallidos = publisher.publish(List.of(evento("e1", "t"), evento("e2", "t")));

        assertThat(fallidos).containsOnlyKeys("e2");
        assertThat(fallidos.get("e2")).contains("ProvisionedThroughputExceededException");
    }

    @Test
    void siFallaLaLlamadaCompletaPropagaLaExcepcionParaReintentar() {
        when(client.putRecords(any(PutRecordsRequest.class)))
                .thenThrow(ProvisionedThroughputExceededException.builder().message("throttled").build());

        assertThatThrownBy(() -> publisher.publish(List.of(evento("e1", "t"))))
                .isInstanceOf(ProvisionedThroughputExceededException.class);
    }

    @Test
    void loteVacioNoLlamaAKinesis() {
        assertThat(publisher.publish(List.of())).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void exigeNombreDeStream() {
        assertThatThrownBy(() -> new KinesisEventStreamPublisher(client, " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KINESIS_STREAM_NAME");
    }
}
