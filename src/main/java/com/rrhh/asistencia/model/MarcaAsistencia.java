package com.rrhh.asistencia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "asistencia_marca_asistencia")
public class MarcaAsistencia {
    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;
    @Column(name = "trabajador_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String trabajadorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMarca tipo;
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;
    @Column(nullable = false, length = 50)
    private String origen;
    @Column(nullable = false)
    private boolean activo = true;
    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
