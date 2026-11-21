-- Outbox de eventos de asistencia (ADR-019): la marca y su evento se escriben en la misma transacción;
-- OutboxPublisher los envía después al stream (Kinesis en AWS, Redis Streams en local).
CREATE TABLE asistencia_outbox_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    intentos INT NOT NULL DEFAULT 0,
    creado_en DATETIME NOT NULL,
    publicado_en DATETIME NULL,
    ultimo_error TEXT NULL
);
CREATE INDEX idx_asistencia_outbox_estado ON asistencia_outbox_event (estado, creado_en);
