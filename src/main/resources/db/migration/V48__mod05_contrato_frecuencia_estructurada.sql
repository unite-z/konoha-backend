-- RF-SER-002: la frecuencia pactada del contrato se separa en cantidad y período (por ejemplo, 4 veces al año).
-- Expand: solo se añaden columnas, nulas para los contratos anteriores. `frecuencia_pactada` (texto) se conserva:
-- la api la sigue escribiendo (con la frase armada) y leyendo, así que la versión anterior sigue funcionando.
ALTER TABLE mod05_contrato_detalle
 ADD COLUMN frecuencia_cantidad integer NULL
  CONSTRAINT mod05_contrato_frecuencia_cantidad CHECK (frecuencia_cantidad BETWEEN 1 AND 365),
 ADD COLUMN frecuencia_unidad varchar(10) NULL
  CONSTRAINT mod05_contrato_frecuencia_unidad CHECK (frecuencia_unidad IN ('Dia','Semana','Mes','Anio')),
 ADD CONSTRAINT mod05_contrato_frecuencia_par CHECK ((frecuencia_cantidad IS NULL) = (frecuencia_unidad IS NULL));
