CREATE TABLE asistencia_marca_asistencia (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    trabajador_id CHAR(36) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    fecha_hora DATETIME NOT NULL,
    origen VARCHAR(50) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en DATETIME NOT NULL
);

CREATE INDEX idx_marca_tenant ON asistencia_marca_asistencia (tenant_id, activo);
CREATE INDEX idx_marca_trabajador ON asistencia_marca_asistencia (trabajador_id, fecha_hora);
