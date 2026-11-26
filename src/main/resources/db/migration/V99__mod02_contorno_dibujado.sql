-- AV-430: contornos dibujados sobre el mapa. Solo aditiva: las geometrías existentes
-- quedan como 'capa' (referencia del campus) y las nuevas se marcan como 'dibujo'.
ALTER TABLE mod02_mapa_geometria
    ADD COLUMN origen varchar(10) NOT NULL DEFAULT 'capa'
        CONSTRAINT mod02_mapa_geometria_origen_ck CHECK (origen IN ('capa', 'dibujo')),
    ADD COLUMN fecha_creacion timestamptz NOT NULL DEFAULT now();

CREATE INDEX mod02_mapa_geometria_origen_idx ON mod02_mapa_geometria (origen);
