-- RF-INV-011: cancelar el seguimiento de un ejemplar marcado con atención especial (por error al marcar, o
-- porque el riesgo ya no corresponde). No se borra: queda como histórico, igual que el resto del módulo.
ALTER TABLE mod05_atencion_especial
    ADD COLUMN cancelado boolean NOT NULL DEFAULT false,
    ADD COLUMN motivo_cancelacion text NULL,
    ADD COLUMN fecha_cancelacion timestamp with time zone NULL,
    ADD COLUMN usuario_cancelacion_id bigint NULL REFERENCES mod05_usuario(id);
