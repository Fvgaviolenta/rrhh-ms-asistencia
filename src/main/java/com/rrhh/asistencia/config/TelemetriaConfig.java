package com.rrhh.asistencia.config;

import com.rrhh.asistencia.security.TenantContext;
import com.rrhh.asistencia.service.TelemetriaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import java.time.Clock;

/**
 * Telemetría de APIs (fase 5 de streaming, piloto en asistencia). Solo con RRHH_TELEMETRY_ENABLED=true,
 * que Terraform entrega en AWS; en local y en los tests no hay filtro, publicador ni (sin Kinesis de
 * eventos) {@link KinesisClient}. Mejor esfuerzo, sin outbox.
 *
 * <p>El filtro corre solo dentro de la cadena de Spring Security ({@code SecurityConfig}); su registro
 * de servlet queda deshabilitado para que no corra dos veces.
 */
@Configuration
@ConditionalOnProperty(prefix = "rrhh.telemetry", name = "enabled", havingValue = "true")
public class TelemetriaConfig {

    @Bean(initMethod = "iniciar", destroyMethod = "close")
    TelemetriaPublisher telemetriaPublisher(KinesisClient kinesisClient,
                                            @Value("${rrhh.telemetry.stream-name:}") String streamName) {
        return new TelemetriaPublisher(kinesisClient, streamName, TelemetriaPublisher.CAPACIDAD,
                TelemetriaPublisher.MAX_LOTE, TelemetriaPublisher.INTERVALO);
    }

    @Bean
    TelemetriaHttpFilter telemetriaHttpFilter(TenantContext tenantContext, TelemetriaPublisher publisher) {
        return new TelemetriaHttpFilter(tenantContext, publisher, Clock.systemUTC());
    }

    @Bean
    FilterRegistrationBean<TelemetriaHttpFilter> telemetriaHttpFilterRegistration(TelemetriaHttpFilter filter) {
        FilterRegistrationBean<TelemetriaHttpFilter> registro = new FilterRegistrationBean<>(filter);
        registro.setEnabled(false);
        return registro;
    }
}
