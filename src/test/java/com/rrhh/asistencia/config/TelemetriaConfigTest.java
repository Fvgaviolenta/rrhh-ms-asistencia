package com.rrhh.asistencia.config;

import com.rrhh.asistencia.security.TenantContext;
import com.rrhh.asistencia.service.EventStreamPublisher;
import com.rrhh.asistencia.service.KinesisEventStreamPublisher;
import com.rrhh.asistencia.service.TelemetriaPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelemetriaConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StreamingConfig.class, TelemetriaConfig.class, TenantContext.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withPropertyValues("rrhh.kinesis.region=us-east-1");

    @Test
    void sinBanderaNoHayFiltroPublicadorNiCliente() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(TelemetriaHttpFilter.class)
                .doesNotHaveBean(TelemetriaPublisher.class).doesNotHaveBean(KinesisClient.class));
    }

    @Test
    void banderaSinNombreDeStreamNoArranca() {
        runner.withPropertyValues("rrhh.telemetry.enabled=true", "rrhh.telemetry.stream-name= ")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().hasRootCauseMessage("RRHH_TELEMETRY_ENABLED=true requiere TELEMETRY_STREAM_NAME"));
    }

    @Test
    void telemetriaSolaCreaClienteYNoPublicadorDeEventos() {
        runner.withPropertyValues("rrhh.telemetry.enabled=true", "rrhh.telemetry.stream-name=rrhh-api-telemetry")
                .run(ctx -> assertThat(ctx).hasSingleBean(KinesisClient.class).hasSingleBean(TelemetriaPublisher.class)
                        .hasSingleBean(TelemetriaHttpFilter.class).doesNotHaveBean(EventStreamPublisher.class));
    }

    @Test
    void conLasDosBanderasElClienteEsUnoSolo() {
        runner.withPropertyValues("rrhh.telemetry.enabled=true", "rrhh.telemetry.stream-name=rrhh-api-telemetry",
                        "rrhh.kinesis.enabled=true", "rrhh.kinesis.stream-name=rrhh-events")
                .run(ctx -> assertThat(ctx).hasSingleBean(KinesisClient.class).hasSingleBean(TelemetriaPublisher.class)
                        .getBean(EventStreamPublisher.class).isInstanceOf(KinesisEventStreamPublisher.class));
    }
}
