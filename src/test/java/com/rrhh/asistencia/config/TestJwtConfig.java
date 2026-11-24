package com.rrhh.asistencia.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

@TestConfiguration
public class TestJwtConfig {
    /** Empresa del token "admin-suspendida"; TenantSuspendidoIT la crea en identity_tenant y la suspende. */
    public static final String TENANT_SUSPENDIBLE = "99999999-9999-9999-9999-999999999999";

    /**
     * Tokens: "admin-suspendida" (Admin de RRHH de TENANT_SUSPENDIBLE), "admin-plataforma" (sin tenant),
     * "admin-plataforma-con-tenant" y "operador-con-tenant" (rol de plataforma con un custom:tenant_id antiguo); cualquier otro es el
     * Admin de RRHH del tenant demo, o Trabajador si termina en "-trabajador".
     */
    private static Map<String, Object> claims(String token) {
        return switch (token) {
            case "admin-suspendida" -> Map.of(
                    "email", "admin.suspendida@rrhh.local",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "Admin de RRHH");
            case "admin-plataforma" -> Map.of(
                    "email", "owner@gmail.com",
                    "custom:role", "AdminPlataforma");
            case "admin-plataforma-con-tenant" -> Map.of(
                    "email", "owner@gmail.com",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "AdminPlataforma");
            case "operador-con-tenant" -> Map.of(
                    "email", "operador@rrhh.local",
                    "custom:tenant_id", TENANT_SUSPENDIBLE,
                    "custom:role", "OperadorSaaS");
            default -> Map.of(
                    "email", "admin.demo@rrhh.local",
                    "custom:tenant_id", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    "custom:role", token.endsWith("-trabajador") ? "Trabajador" : "Admin de RRHH");
        };
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("test")
                .claims(c -> c.putAll(claims(token)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
