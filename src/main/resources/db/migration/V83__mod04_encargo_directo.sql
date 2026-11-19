ALTER TABLE mod04_encargo
 DROP CONSTRAINT mod04_encargo_periodicidad_check,
 DROP CONSTRAINT mod04_encargo_check1,
 ALTER COLUMN fecha_reunion DROP NOT NULL,
 ADD CONSTRAINT mod04_encargo_modalidad_check CHECK (
    (periodicidad = 'Directo' AND fecha_reunion IS NULL AND fecha_fin = plazo_previsto)
    OR (periodicidad = 'Semanal' AND fecha_reunion IS NOT NULL AND fecha_fin - fecha_inicio < 7)
    OR (periodicidad = 'Quincenal' AND fecha_reunion IS NOT NULL AND fecha_fin - fecha_inicio < 14)
 );
