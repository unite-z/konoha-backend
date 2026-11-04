-- AV-363: valores iniciales de la sección por servicio periódico, para «Usar valores iniciales» al
-- configurarlo. Solo la estructura: los datos se cargan en la precarga del perfil dev, nunca aquí.

CREATE TABLE mod03_valor_inicial_frecuencia (
    tipo_servicio_id bigint NOT NULL REFERENCES mod05_tiposervicio (id),
    estacion varchar(10) NOT NULL CHECK (estacion IN ('Verano', 'Otoño', 'Invierno', 'Primavera')),
    frecuencia_teorica_dias integer NOT NULL CHECK (frecuencia_teorica_dias > 0),
    frecuencia_practica_dias integer CHECK (frecuencia_practica_dias > 0),
    frecuencia_maxima_dias integer CHECK (frecuencia_maxima_dias > 0),
    PRIMARY KEY (tipo_servicio_id, estacion)
);

CREATE TABLE mod03_valor_inicial_duracion (
    tipo_servicio_id bigint PRIMARY KEY REFERENCES mod05_tiposervicio (id),
    duracion_maxima_dias integer NOT NULL CHECK (duracion_maxima_dias > 0),
    dias_limpieza integer CHECK (dias_limpieza >= 0),
    hora_inicio time,
    hora_fin time
);
