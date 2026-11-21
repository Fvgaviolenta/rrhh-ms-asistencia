package com.rrhh.asistencia.config;

import com.rrhh.asistencia.service.EventStreamPublisher;
import com.rrhh.asistencia.service.KinesisEventStreamPublisher;
import com.rrhh.asistencia.service.RedisEventStreamPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Elige el destino del stream (ADR-019). Kinesis manda si RRHH_KINESIS_ENABLED=true (AWS, credenciales de LabRole);
 * si no, RRHH_STREAMING_BACKEND=redis usa Redis Streams (local). Sin ninguno no hay publicador.
 */
@Configuration
public class StreamingConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "rrhh.kinesis", name = "enabled", havingValue = "true")
    KinesisClient kinesisClient(@Value("${rrhh.kinesis.region}") String region) {
        return KinesisClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "rrhh.kinesis", name = "enabled", havingValue = "true")
    EventStreamPublisher kinesisEventStreamPublisher(KinesisClient kinesisClient,
                                                     @Value("${rrhh.kinesis.stream-name:}") String streamName) {
        return new KinesisEventStreamPublisher(kinesisClient, streamName);
    }

    @Bean
    @ConditionalOnExpression("'${rrhh.streaming.backend:}' == 'redis' and '${rrhh.kinesis.enabled:false}' != 'true'")
    EventStreamPublisher redisEventStreamPublisher(StringRedisTemplate redis,
                                                   @Value("${rrhh.streaming.redis-stream-key:rrhh:events}") String streamKey,
                                                   @Value("${rrhh.streaming.redis-max-len:10000}") long maxLen) {
        return new RedisEventStreamPublisher(redis, streamKey, maxLen);
    }
}
