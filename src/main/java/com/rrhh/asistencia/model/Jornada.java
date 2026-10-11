package com.rrhh.asistencia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "asistencia_jornada")
public class Jornada {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;

    @Column(name = "trabajador_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String trabajadorId;

    @Column(name = "turno_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String turnoId;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private boolean activo = true;
}
