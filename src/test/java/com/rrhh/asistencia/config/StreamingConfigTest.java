package com.rrhh.asistencia.config;

import com.rrhh.asistencia.service.EventStreamPublisher;
import com.rrhh.asistencia.service.KinesisEventStreamPublisher;
import com.rrhh.asistencia.service.RedisEventStreamPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StreamingConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StreamingConfig.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withPropertyValues("rrhh.kinesis.region=us-east-1");

    @Test
    void sinConfiguracionNoHayPublicador() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(EventStreamPublisher.class));
    }

    @Test
    void kinesisHabilitadoUsaKinesis() {
        runner.withPropertyValues("rrhh.kinesis.enabled=true", "rrhh.kinesis.stream-name=rrhh-events")
                .run(ctx -> assertThat(ctx).getBean(EventStreamPublisher.class).isInstanceOf(KinesisEventStreamPublisher.class));
    }

    @Test
    void backendRedisUsaRedisStreams() {
        runner.withPropertyValues("rrhh.streaming.backend=redis")
                .run(ctx -> assertThat(ctx).getBean(EventStreamPublisher.class).isInstanceOf(RedisEventStreamPublisher.class));
    }

    @Test
    void siAmbosEstanConfiguradosGanaKinesis() {
        runner.withPropertyValues("rrhh.kinesis.enabled=true", "rrhh.kinesis.stream-name=rrhh-events", "rrhh.streaming.backend=redis")
                .run(ctx -> assertThat(ctx).getBean(EventStreamPublisher.class).isInstanceOf(KinesisEventStreamPublisher.class));
    }

    @Test
    void kinesisSinNombreDeStreamNoArranca() {
        runner.withPropertyValues("rrhh.kinesis.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
