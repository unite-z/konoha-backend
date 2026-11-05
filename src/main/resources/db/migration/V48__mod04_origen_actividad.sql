ALTER TABLE mod04_actividad
 ADD COLUMN clase_actividad_id bigint REFERENCES mod04_clase_actividad(id),
 ADD COLUMN fecha_solicitud date,
 ADD COLUMN incidencia_id bigint REFERENCES mod03_incidencia(id);

UPDATE mod04_actividad a SET clase_actividad_id=t.clase_id
 FROM mod04_tipo_actividad t WHERE t.id=a.tipo_actividad_id;

CREATE FUNCTION mod04_fijar_clase_actividad() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.clase_actividad_id IS NULL THEN
  SELECT clase_id INTO NEW.clase_actividad_id FROM mod04_tipo_actividad
   WHERE id=NEW.tipo_actividad_id;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER mod04_actividad_clase_historica BEFORE INSERT ON mod04_actividad
 FOR EACH ROW EXECUTE FUNCTION mod04_fijar_clase_actividad();
