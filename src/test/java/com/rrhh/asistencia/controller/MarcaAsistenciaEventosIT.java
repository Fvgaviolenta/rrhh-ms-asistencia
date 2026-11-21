package com.rrhh.asistencia.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.asistencia.AsistenciaApplication;
import com.rrhh.asistencia.config.TestJwtConfig;
import com.rrhh.asistencia.model.Jornada;
import com.rrhh.asistencia.model.OutboxEvent;
import com.rrhh.asistencia.model.Turno;
import com.rrhh.asistencia.repository.JornadaRepository;
import com.rrhh.asistencia.repository.OutboxEventRepository;
import com.rrhh.asistencia.repository.TurnoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Eventos de asistencia en el outbox (ADR-019): tipo, tenant, enriquecimiento con el turno y datos que no viajan. */
@SpringBootTest(classes = AsistenciaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MarcaAsistenciaEventosIT {
    private static final String TENANT_DEL_JWT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final ZoneId ZONA = ZoneId.of("America/Santiago");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private JornadaRepository jornadaRepository;

    private String registrar(String body) throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/asistencia/registro")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).path("datos").path("id").asText();
    }

    private static String body(String trabajadorId) {
        return "{\"trabajador_id\":\"" + trabajadorId + "\",\"tipo\":\"ENTRADA\",\"origen\":\"web\"}";
    }

    private void asignarTurnoHoy(String trabajadorId, LocalTime horaInicio) {
        Turno turno = new Turno();
        turno.setId(UUID.randomUUID().toString());
        turno.setTenantId(TENANT_DEL_JWT);
        turno.setNombre("Mañana");
        turno.setHoraInicio(horaInicio);
        turno.setHoraFin(horaInicio.plusHours(9));
        turnoRepository.save(turno);
        Jornada jornada = new Jornada();
        jornada.setId(UUID.randomUUID().toString());
        jornada.setTenantId(TENANT_DEL_JWT);
        jornada.setTrabajadorId(trabajadorId);
        jornada.setTurnoId(turno.getId());
        jornada.setFecha(LocalDate.now(ZONA));
        jornadaRepository.save(jornada);
    }

    private OutboxEvent unicoEvento(String marcaId, String eventType) {
        List<OutboxEvent> eventos = outboxRepository.findByAggregateIdOrderByCreadoEnAsc(marcaId).stream()
                .filter(e -> e.getEventType().equals(eventType)).toList();
        assertThat(eventos).hasSize(1);
        return eventos.get(0);
    }

    @Test
    void registrarMarcaDejaElEventoPendienteEnriquecidoConElTurno() throws Exception {
        String trabajadorId = UUID.randomUUID().toString();
        asignarTurnoHoy(trabajadorId, LocalTime.of(8, 0));

        String marcaId = registrar(body(trabajadorId));

        OutboxEvent evento = unicoEvento(marcaId, "asistencia.marca.registrada");
        assertThat(evento.getEstado()).isEqualTo(OutboxEvent.PENDIENTE);
        assertThat(evento.getTenantId()).isEqualTo(TENANT_DEL_JWT);
        JsonNode payload = objectMapper.readTree(evento.getPayload());
        assertThat(payload.path("trabajadorId").asText()).isEqualTo(trabajadorId);
        assertThat(payload.path("tipo").asText()).isEqualTo("ENTRADA");
        assertThat(payload.path("origen").asText()).isEqualTo("web");
        assertThat(payload.path("turnoHoraInicio").asText()).isEqualTo("08:00");
        assertThat(payload.path("zona").asText()).isEqualTo("America/Santiago");
        assertThat(Instant.parse(payload.path("fechaHora").asText())).isNotNull();
    }

    @Test
    void sinJornadaElEventoNoTraeTurno() throws Exception {
        String marcaId = registrar(body(UUID.randomUUID().toString()));

        JsonNode payload = objectMapper.readTree(unicoEvento(marcaId, "asistencia.marca.registrada").getPayload());
        assertThat(payload.has("turnoHoraInicio")).isFalse();
        assertThat(payload.path("zona").asText()).isEqualTo("America/Santiago");
    }

    @Test
    void elTenantDelEventoSaleDelJwtYNoDelBody() throws Exception {
        String trabajadorId = UUID.randomUUID().toString();
        String bodyConOtroTenant = "{\"trabajador_id\":\"" + trabajadorId
                + "\",\"tipo\":\"ENTRADA\",\"origen\":\"web\",\"tenant_id\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\"}";

        String marcaId = registrar(bodyConOtroTenant);

        assertThat(unicoEvento(marcaId, "asistencia.marca.registrada").getTenantId()).isEqualTo(TENANT_DEL_JWT);
    }

    @Test
    void editarMarcaEmiteAsistenciaEditadaSinElMotivo() throws Exception {
        String marcaId = registrar(body(UUID.randomUUID().toString()));
        Instant nuevaHora = Instant.now().minusSeconds(3600);

        mockMvc.perform(put("/api/v1/asistencia/" + marcaId + "/editar")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha_hora\":\"" + nuevaHora + "\",\"motivo\":\"Olvidó marcar, RUT 12.345.678-9\"}"))
                .andExpect(status().isOk());

        OutboxEvent evento = unicoEvento(marcaId, "asistencia.editada");
        assertThat(evento.getTenantId()).isEqualTo(TENANT_DEL_JWT);
        assertThat(evento.getPayload()).doesNotContain("Olvidó").doesNotContain("12.345.678-9");
        JsonNode payload = objectMapper.readTree(evento.getPayload());
        assertThat(Instant.parse(payload.path("fechaHora").asText())).isEqualTo(nuevaHora);
        assertThat(payload.path("fechaHoraAnterior").isTextual()).isTrue();
    }
}
