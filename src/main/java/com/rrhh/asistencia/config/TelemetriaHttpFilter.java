package com.rrhh.asistencia.config;

import com.rrhh.asistencia.security.TenantContext;
import com.rrhh.asistencia.service.RegistroTelemetria;
import com.rrhh.asistencia.service.TelemetriaPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

/**
 * Arma un registro de telemetría por request terminado (fase 5 de streaming, spec S2 y S3).
 *
 * <p>Va en la cadena de Spring Security después de {@code TenantCleanupFilter}, así su {@code finally}
 * lee el tenant antes de que ese filtro lo limpie. La ruta es la plantilla de Spring MVC
 * ({@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}), nunca la URI real; sin plantilla, o con el
 * comodín {@code /**} del handler de recursos estáticos, va {@link #SIN_RUTA}. Una falla al armar o
 * entregar el registro nunca cambia la respuesta. {@code /actuator/**} no genera registro.
 */
public class TelemetriaHttpFilter extends OncePerRequestFilter {
    public static final String SIN_RUTA = "SIN_RUTA";

    private static final Logger log = LoggerFactory.getLogger(TelemetriaHttpFilter.class);
    private static final String COMODIN = "/**";

    private final TenantContext tenantContext;
    private final TelemetriaPublisher publisher;
    private final Clock clock;

    public TelemetriaHttpFilter(TenantContext tenantContext, TelemetriaPublisher publisher, Clock clock) {
        this.tenantContext = tenantContext;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI().substring(request.getContextPath().length());
        return uri.equals("/actuator") || uri.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        Exception error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            error = e;
            throw e;
        } finally {
            registrar(request, response, inicio, error);
        }
    }

    private void registrar(HttpServletRequest request, HttpServletResponse response, long inicio, Exception error) {
        try {
            long latenciaMs = (System.nanoTime() - inicio) / 1_000_000;
            int status = response.getStatus();
            // Una excepción que escapa con un status sin error termina en un 500 para el cliente.
            if (error != null && status < 400) status = 500;
            publisher.registrar(RegistroTelemetria.de(request.getMethod(), ruta(request), status, latenciaMs,
                    tenantContext.tenantIdOrNull(), Instant.now(clock)));
        } catch (RuntimeException e) {
            log.debug("Telemetría: no se pudo registrar el request ({})", e.getClass().getSimpleName());
        }
    }

    static String ruta(HttpServletRequest request) {
        Object patron = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(patron instanceof String p) || p.isBlank() || p.equals(COMODIN)) return SIN_RUTA;
        return p;
    }
}
