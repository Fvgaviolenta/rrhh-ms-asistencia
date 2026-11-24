package com.rrhh.asistencia.controller;

import com.rrhh.asistencia.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fuera de alcance de ADR-020: un rol de plataforma no registra ni lee marcas de una empresa, aunque su token
 * conserve un custom:tenant_id antiguo (las rutas son isAuthenticated()).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class RolDePlataformaEnAsistenciaIT {

    private static final String TRABAJADOR = "ffffffff-ffff-ffff-ffff-ffffffffffff";

    @Autowired private MockMvc mockMvc;

    @Test
    void rolDePlataformaNoRegistraNiLeeMarcas() throws Exception {
        for (String token : new String[]{"admin-plataforma-con-tenant", "operador-con-tenant"}) {
            mockMvc.perform(post("/api/v1/asistencia/registro").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"trabajador_id\":\"" + TRABAJADOR + "\",\"tipo\":\"ENTRADA\",\"origen\":\"web\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/asistencia/trabajador/{id}", TRABAJADOR).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/asistencia/trabajador/{id}/hoy", TRABAJADOR).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }
}
