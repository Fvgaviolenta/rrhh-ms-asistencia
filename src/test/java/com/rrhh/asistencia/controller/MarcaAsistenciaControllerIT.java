package com.rrhh.asistencia.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.asistencia.AsistenciaApplication;
import com.rrhh.asistencia.config.TestJwtConfig;
import com.rrhh.asistencia.dto.request.EditarMarcaRequest;
import com.rrhh.asistencia.dto.request.RegistrarMarcaRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AsistenciaApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MarcaAsistenciaControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminListaMarcasDelTenant() throws Exception {
        mockMvc.perform(get("/api/v1/marcas-asistencia")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void listarMarcasPorTrabajador() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/trabajador/test-trabajador-id")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void obtenerMarcaHoy() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/trabajador/test-trabajador-id/hoy")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());
    }

    @Test
    void listarMarcasPorPeriodo() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/trabajador/test-trabajador-id/periodo")
                        .param("fecha_inicio", "2024-01-01")
                        .param("fecha_fin", "2024-01-31")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isArray());
    }

    @Test
    void registrarMarcaEntrada() throws Exception {
        RegistrarMarcaRequest request = new RegistrarMarcaRequest(
                "test-trabajador-id",
                RegistrarMarcaRequest.TipoMarcaEnum.ENTRADA,
                "APP_MOVIL"
        );

        mockMvc.perform(post("/api/v1/asistencia/registro")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Marca registrada exitosamente"));
    }

    @Test
    void registrarMarcaSalida() throws Exception {
        RegistrarMarcaRequest request = new RegistrarMarcaRequest(
                "test-trabajador-id",
                RegistrarMarcaRequest.TipoMarcaEnum.SALIDA,
                "APP_MOVIL"
        );

        mockMvc.perform(post("/api/v1/asistencia/registro")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void editarMarca() throws Exception {
        EditarMarcaRequest request = new EditarMarcaRequest(
                Instant.now().minusSeconds(3600),
                "Corrección manual"
        );

        mockMvc.perform(put("/api/v1/asistencia/test-marca-id/editar")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Marca editada exitosamente"));
    }

    @Test
    void obtenerResumenAsistencia() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/resumen")
                        .param("fecha_inicio", "2024-01-01")
                        .param("fecha_fin", "2024-01-31")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").exists());
    }

    @Test
    void statusDelServicio() throws Exception {
        mockMvc.perform(get("/api/v1/asistencia/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("OPERATIVO"));
    }

    @Test
    void registrarMarcaSinToken() throws Exception {
        RegistrarMarcaRequest request = new RegistrarMarcaRequest(
                "test-trabajador-id",
                RegistrarMarcaRequest.TipoMarcaEnum.ENTRADA,
                "APP_MOVIL"
        );

        mockMvc.perform(post("/api/v1/asistencia/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editarMarcaSinPermisos() throws Exception {
        // Este test asume que el token de test no tiene rol ADMIN_RRHH
        EditarMarcaRequest request = new EditarMarcaRequest(
                Instant.now(),
                "Motivo"
        );

        mockMvc.perform(put("/api/v1/asistencia/test-marca-id/editar")
                        .header("Authorization", "Bearer test-token-trabajador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
