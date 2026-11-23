package com.rrhh.asistencia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordsRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.PutRecordsResultEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TelemetriaPublisherTest {
    private final KinesisClient client = mock(KinesisClient.class);

    private static RegistroTelemetria reg(String tenant) {
        return RegistroTelemetria.de("GET", "/api/v1/asistencia/{marca_id}/editar", 200, 12, tenant,
                Instant.parse("2026-11-22T12:00:00Z"));
    }

    private TelemetriaPublisher nuevo(int capacidad) {
        return new TelemetriaPublisher(client, "rrhh-api-telemetry", capacidad, 500, Duration.ofHours(1));
    }

    private static PutRecordsResponse ok() {
        return PutRecordsResponse.builder().failedRecordCount(0).build();
    }

    @Test
    void enviaEnLotesDeHasta500ConPartitionKeyDelTenant() {
        when(client.putRecords(any(PutRecordsRequest.class))).thenReturn(ok());
        TelemetriaPublisher p = nuevo(5000);
        for (int i = 0; i < 1199; i++) p.registrar(reg("t1"));
        p.registrar(reg(null));
        assertThat(p.enviarPendientes()).isEqualTo(1200);
        ArgumentCaptor<PutRecordsRequest> c = ArgumentCaptor.forClass(PutRecordsRequest.class);
        verify(client, times(3)).putRecords(c.capture());
        assertThat(c.getAllValues()).extracting(r -> r.records().size()).containsExactly(500, 500, 200);
        assertThat(c.getAllValues()).allMatch(r -> r.streamName().equals("rrhh-api-telemetry"));
        assertThat(c.getValue().records().get(0).partitionKey()).isEqualTo("t1");
        assertThat(c.getValue().records().get(199).partitionKey()).isEqualTo("asistencia");
    }

    @Test
    void jsonTieneExactamenteLasClavesDelContrato() throws Exception {
        Map<String, Object> m = new ObjectMapper().readValue(reg(null).toJson(), new TypeReference<>() {});
        assertThat(m).containsOnlyKeys("tipo", "servicio", "metodo", "ruta", "status", "latenciaMs", "tenantId", "ocurridoEn");
        assertThat(m).containsEntry("tipo", "api.request").containsEntry("servicio", "asistencia")
                .containsEntry("status", 200).containsEntry("latenciaMs", 12).containsEntry("tenantId", null)
                .containsEntry("ocurridoEn", "2026-11-22T12:00:00Z");
    }

    @Test
    void colaLlenaDescartaYCuentaSinBloquear() {
        TelemetriaPublisher p = nuevo(3);
        assertThat(List.of(p.registrar(reg("t1")), p.registrar(reg("t1")), p.registrar(reg("t1")), p.registrar(reg("t1"))))
                .containsExactly(true, true, true, false);
        assertThat(p.descartados()).isEqualTo(1);
    }

    @Test
    void falloDeKinesisDescartaElLoteSinReintentar(CapturedOutput out) {
        when(client.putRecords(any(PutRecordsRequest.class))).thenThrow(SdkClientException.create("sin red"));
        TelemetriaPublisher p = nuevo(5000);
        p.registrar(reg("t1"));
        p.registrar(reg("t1"));
        assertThat(p.enviarPendientes()).isZero();
        assertThat(p.enviarPendientes()).isZero();
        verify(client, times(1)).putRecords(any(PutRecordsRequest.class));
        assertThat(p.descartados()).isEqualTo(2);
        assertThat(out.getOut()).contains("WARN").contains("2").doesNotContain("/api/v1/asistencia/{marca_id}/editar");
    }

    @Test
    void registrosConErrorCodeSeDescartanSinReintento() {
        PutRecordsResultEntry bien = PutRecordsResultEntry.builder().sequenceNumber("1").shardId("s").build();
        PutRecordsResultEntry mal = PutRecordsResultEntry.builder().errorCode("ProvisionedThroughputExceededException").errorMessage("x").build();
        when(client.putRecords(any(PutRecordsRequest.class)))
                .thenReturn(PutRecordsResponse.builder().failedRecordCount(2).records(bien, mal, mal).build());
        TelemetriaPublisher p = nuevo(5000);
        for (int i = 0; i < 3; i++) p.registrar(reg("t1"));
        assertThat(p.enviarPendientes()).isEqualTo(1);
        assertThat(p.descartados()).isEqualTo(2);
        assertThat(p.enviarPendientes()).isZero();
        verify(client, times(1)).putRecords(any(PutRecordsRequest.class));
    }

    @Test
    void alLlegarA500EnviaSinEsperarElIntervalo() {
        when(client.putRecords(any(PutRecordsRequest.class))).thenReturn(ok());
        try (TelemetriaPublisher p = nuevo(5000)) {
            p.iniciar();
            for (int i = 0; i < 500; i++) p.registrar(reg("t1"));
            verify(client, timeout(2000)).putRecords(any(PutRecordsRequest.class));
        }
    }

    @Test
    void exigeNombreDeStream() {
        for (String malo : new String[]{null, "", " "}) {
            assertThatThrownBy(() -> new TelemetriaPublisher(client, malo, 5000, 500, Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("RRHH_TELEMETRY_ENABLED=true requiere TELEMETRY_STREAM_NAME");
        }
    }
}
