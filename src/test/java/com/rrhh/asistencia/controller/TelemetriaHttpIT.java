package com.rrhh.asistencia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.asistencia.AsistenciaApplication;
import com.rrhh.asistencia.config.TelemetriaHttpFilter;
import com.rrhh.asistencia.config.TestJwtConfig;
import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.service.RegistroTelemetria;
import com.rrhh.asistencia.service.TelemetriaPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.kinesis.KinesisClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AsistenciaApplication.class,
        properties = {"rrhh.telemetry.enabled=true", "rrhh.telemetry.stream-name=rrhh-api-telemetry"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class TelemetriaHttpIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private List<FilterRegistrationBean<?>> registros;

    @MockBean
    private KinesisClient kinesisClient;

    @MockBean
    private TelemetriaPublisher publisher;

    private RegistroTelemetria capturado() {
        ArgumentCaptor<RegistroTelemetria> c = ArgumentCaptor.forClass(RegistroTelemetria.class);
        verify(publisher, times(1)).registrar(c.capture());
        return c.getValue();
    }

    @Test
    void requestAutenticadoRegistraPlantillaYTenantDelJwt() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/trabajador/test-trabajador-id/periodo")
                        .param("fecha_inicio", "2024-01-01").param("fecha_fin", "2024-01-31")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
        RegistroTelemetria r = capturado();
        assertThat(r).extracting(RegistroTelemetria::metodo, RegistroTelemetria::ruta, RegistroTelemetria::status, RegistroTelemetria::tenantId)
                .containsExactly("GET", "/api/v1/asistencia/trabajador/{trabajador_id}/periodo", 200, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(r.toJson()).doesNotContain("test-trabajador-id").doesNotContain("2024-01-01")
                .doesNotContain("admin.demo@rrhh.local").doesNotContain("test-token");
    }

    @Test
    void marcaInexistenteRegistra404ConPlantilla() throws Exception {
        mockMvc.perform(put("/api/v1/asistencia/marca-que-no-existe/editar").header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditarMarcaRequest(Instant.parse("2026-11-22T12:00:00Z"), "corrección"))))
                .andExpect(status().isNotFound());
        assertThat(capturado()).extracting(RegistroTelemetria::ruta, RegistroTelemetria::status)
                .containsExactly("/api/v1/asistencia/{marca_id}/editar", 404);
    }

    @Test
    void rutaSinControladorRegistraSinRuta() throws Exception {
        int status = mockMvc.perform(get("/api/v1/no-existe").header("Authorization", "Bearer test-token"))
                .andReturn().getResponse().getStatus();
        assertThat(capturado()).extracting(RegistroTelemetria::ruta, RegistroTelemetria::status).containsExactly("SIN_RUTA", status);
    }

    @Test
    void requestSinTokenRegistraTenantNulo() throws Exception {
        mockMvc.perform(get("/api/v1/marcas-asistencia")).andExpect(status().isUnauthorized());
        assertThat(capturado()).extracting(RegistroTelemetria::ruta, RegistroTelemetria::status, RegistroTelemetria::tenantId)
                .containsExactly("SIN_RUTA", 401, null);
    }

    @Test
    void actuatorNoGeneraRegistroYElFiltroNoEsDeServlet() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        verify(publisher, never()).registrar(any());
        assertThat(registros.stream().filter(b -> b.getFilter() instanceof TelemetriaHttpFilter))
                .singleElement().satisfies(b -> assertThat(b.isEnabled()).isFalse());
    }
}
