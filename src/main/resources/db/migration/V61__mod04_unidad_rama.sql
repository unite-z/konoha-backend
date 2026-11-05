-- AV-79: permite expresar en ramas los materiales retirados durante la actividad.
-- MOD-02 conserva su catálogo propio; esta fila amplía el catálogo de unidades de MOD-04.
INSERT INTO mod04_unidad_medida (simbolo, nombre)
VALUES ('rama', 'Rama')
ON CONFLICT (simbolo) DO NOTHING;
