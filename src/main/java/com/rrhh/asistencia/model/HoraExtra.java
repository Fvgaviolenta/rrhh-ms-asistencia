package com.rrhh.asistencia.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "asistencia_hora_extra")
public class HoraExtra {
    @Id @Column(length = 36, columnDefinition = "CHAR(36)") private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)") private String tenantId;
    @Column(name = "trabajador_id", nullable = false, length = 36, columnDefinition = "CHAR(36)") private String trabajadorId;
    @Column(nullable = false) private LocalDate fecha;
    @Column(nullable = false, length = 7) private String periodo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private TipoHoraExtra tipo;
    @Column(name = "cantidad_horas", nullable = false, precision = 6, scale = 2) private BigDecimal cantidadHoras;
    @Column(name = "valor_hora", nullable = false, precision = 14, scale = 2) private BigDecimal valorHora;
    @Column(name = "recargo_porcentaje", nullable = false, precision = 5, scale = 2) private BigDecimal recargoPorcentaje;
    @Column(name = "monto_total", nullable = false, precision = 14, scale = 2) private BigDecimal montoTotal;
    @Column(nullable = false, length = 500) private String motivo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private EstadoHoraExtra estado;
    @Column(name = "aprobado_por", length = 36, columnDefinition = "CHAR(36)") private String aprobadoPor;
    @Column(name = "aprobado_en") private Instant aprobadoEn;
    @Column(name = "liquidacion_id", length = 36, columnDefinition = "CHAR(36)") private String liquidacionId;
    @Column(name = "creado_por", nullable = false, length = 36, columnDefinition = "CHAR(36)") private String creadoPor;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
}
