package com.rrhh.asistencia.config;

import com.rrhh.asistencia.security.TenantContext;
import com.rrhh.asistencia.service.RegistroTelemetria;
import com.rrhh.asistencia.service.TelemetriaPublisher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TelemetriaHttpFilterTest {
    private final TelemetriaPublisher publisher = mock(TelemetriaPublisher.class);
    private final TenantContext tenantContext = new TenantContext();
    private final TelemetriaHttpFilter filtro = new TelemetriaHttpFilter(tenantContext, publisher,
            Clock.fixed(Instant.parse("2026-11-22T12:00:00Z"), ZoneOffset.UTC));
    private final MockHttpServletResponse res = new MockHttpServletResponse();

    @AfterEach
    void limpiar() {
        tenantContext.clear();
    }

    private RegistroTelemetria capturado() {
        ArgumentCaptor<RegistroTelemetria> c = ArgumentCaptor.forClass(RegistroTelemetria.class);
        verify(publisher, times(1)).registrar(c.capture());
        return c.getValue();
    }

    @Test
    void registraPlantillaStatusLatenciaYTenant() throws Exception {
        tenantContext.set(new TenantContext.AuthenticatedUser("t1", "u1", "a@b.cl", "Admin de RRHH", "trab-1", "sub"));
        MockHttpServletRequest req = new MockHttpServletRequest("PUT", "/api/v1/asistencia/abc-123/editar");
        req.setQueryString("rut=11.111.111-1");
        filtro.doFilter(req, res, (rq, rs) -> {
            rq.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/asistencia/{marca_id}/editar");
            ((HttpServletResponse) rs).setStatus(404);
            try { Thread.sleep(15); } catch (InterruptedException e) { throw new IllegalStateException(e); }
        });
        RegistroTelemetria r = capturado();
        assertThat(r).extracting(RegistroTelemetria::tipo, RegistroTelemetria::servicio, RegistroTelemetria::metodo,
                        RegistroTelemetria::ruta, RegistroTelemetria::status, RegistroTelemetria::tenantId, RegistroTelemetria::ocurridoEn)
                .containsExactly("api.request", "asistencia", "PUT", "/api/v1/asistencia/{marca_id}/editar", 404, "t1", "2026-11-22T12:00:00Z");
        assertThat(r.latenciaMs()).isGreaterThanOrEqualTo(15);
        assertThat(r.toJson()).doesNotContain("abc-123").doesNotContain("rut=").doesNotContain("11.111.111-1").doesNotContain("a@b.cl").doesNotContain("trab-1").doesNotContain("u1");
    }

    @Test
    void sinPlantillaOComodinEsSinRuta() throws Exception {
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/v1/no-existe"), res, (rq, rs) -> {});
        MockHttpServletRequest comodin = new MockHttpServletRequest("GET", "/api/v1/otra");
        filtro.doFilter(comodin, new MockHttpServletResponse(),
                (rq, rs) -> rq.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/**"));
        ArgumentCaptor<RegistroTelemetria> c = ArgumentCaptor.forClass(RegistroTelemetria.class);
        verify(publisher, times(2)).registrar(c.capture());
        assertThat(c.getAllValues()).extracting(RegistroTelemetria::ruta).containsExactly("SIN_RUTA", "SIN_RUTA");
    }

    @Test
    void sinTenantRegistraNull() throws Exception {
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/v1/marcas-asistencia"), res, (rq, rs) -> ((HttpServletResponse) rs).setStatus(401));
        assertThat(capturado()).extracting(RegistroTelemetria::tenantId, RegistroTelemetria::status).containsExactly(null, 401);
    }

    @Test
    void excepcionDeLaCadenaSeRegistraComo500YSePropaga() {
        assertThatThrownBy(() -> filtro.doFilter(new MockHttpServletRequest("GET", "/api/v1/x"), res,
                (rq, rs) -> { throw new ServletException("boom"); })).isInstanceOf(ServletException.class);
        assertThat(capturado().status()).isEqualTo(500);
    }

    @Test
    void excepcionConStatusDeErrorConservaElStatus() {
        assertThatThrownBy(() -> filtro.doFilter(new MockHttpServletRequest("GET", "/api/v1/x"), res,
                (rq, rs) -> { ((HttpServletResponse) rs).setStatus(503); throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(capturado().status()).isEqualTo(503);
    }

    @Test
    void fallaDelPublicadorNoCambiaLaRespuesta() throws Exception {
        doThrow(new IllegalStateException("cola rota")).when(publisher).registrar(any());
        filtro.doFilter(new MockHttpServletRequest("POST", "/api/v1/asistencia/registro"), res, (rq, rs) -> {
            ((HttpServletResponse) rs).setStatus(201);
            rs.getWriter().write("{\"ok\":true}");
        });
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getContentAsString()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void actuatorNoGeneraRegistro() throws Exception {
        for (String uri : List.of("/actuator", "/actuator/health", "/actuator/metrics/jvm.memory.used")) {
            filtro.doFilter(new MockHttpServletRequest("GET", uri), new MockHttpServletResponse(), (rq, rs) -> {});
        }
        verify(publisher, never()).registrar(any());
    }
}
