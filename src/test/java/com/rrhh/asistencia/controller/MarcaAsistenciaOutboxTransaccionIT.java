package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.AsistenciaApplication;
import com.rrhh.asistencia.config.TestJwtConfig;
import com.rrhh.asistencia.model.OutboxEvent;
import com.rrhh.asistencia.repository.MarcaAsistenciaRepository;
import com.rrhh.asistencia.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La marca y su evento se escriben en la misma transacción: si el outbox falla, la marca tampoco queda. */
@SpringBootTest(classes = AsistenciaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MarcaAsistenciaOutboxTransaccionIT {
    private static final String TENANT_DEL_JWT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    @Autowired private MockMvc mockMvc;
    @Autowired private MarcaAsistenciaRepository marcaRepository;
    @SpyBean private OutboxEventRepository outboxRepository;

    @Test
    void siNoSePuedeGuardarElEventoLaMarcaSeRevierte() throws Exception {
        String trabajadorId = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("outbox caído")).when(outboxRepository).save(any(OutboxEvent.class));

        mockMvc.perform(post("/api/v1/asistencia/registro")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trabajador_id\":\"" + trabajadorId + "\",\"tipo\":\"ENTRADA\",\"origen\":\"web\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(marcaRepository.findByTenantIdAndTrabajadorIdAndActivoTrueOrderByFechaHoraDesc(TENANT_DEL_JWT, trabajadorId))
                .isEmpty();
    }
}
