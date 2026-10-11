CREATE TABLE asistencia_turno (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_turno_tenant ON asistencia_turno (tenant_id, activo);

CREATE TABLE asistencia_jornada (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    trabajador_id CHAR(36) NOT NULL,
    turno_id CHAR(36) NOT NULL,
    fecha DATE NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_jornada_turno FOREIGN KEY (turno_id) REFERENCES asistencia_turno (id)
);

CREATE INDEX idx_jornada_trabajador_fecha ON asistencia_jornada (tenant_id, trabajador_id, fecha, activo);
