-- AV-363: los valores iniciales de la sección (RN-001, RN-002, RN-017 y RN-018) son parámetros de
-- negocio, no datos de prueba: se cargan en todos los entornos para que «Usar valores iniciales»
-- funcione en UAT y producción. Corrige lo dicho en V40, que los dejaba solo en la precarga dev.
-- Los mismos valores que ValoresIniciales.precargar(); no toca los que ya existan.
WITH valores (servicio, teorica, practica, maxima, duracion, limpieza, hora_inicio, hora_fin) AS (
    VALUES
        ('Corte de césped', 21, 35, 45, 3, 1, NULL::time, NULL::time),
        ('Control fitosanitario', 90, 90, 100, 1, 0, TIME '06:00', TIME '18:00'),
        ('Poda mayor programada', 365, 365, 365, 10, 0, NULL::time, NULL::time)
), frecuencias AS (
    INSERT INTO mod03_valor_inicial_frecuencia (tipo_servicio_id, estacion,
        frecuencia_teorica_dias, frecuencia_practica_dias, frecuencia_maxima_dias)
    SELECT t.id, e, v.teorica, v.practica, v.maxima
    FROM valores v
    JOIN mod05_tiposervicio t ON t.nombre = v.servicio
    CROSS JOIN unnest(ARRAY['Verano', 'Otoño', 'Invierno', 'Primavera']) e
    ON CONFLICT DO NOTHING
)
INSERT INTO mod03_valor_inicial_duracion (tipo_servicio_id, duracion_maxima_dias, dias_limpieza,
    hora_inicio, hora_fin)
SELECT t.id, v.duracion, v.limpieza, v.hora_inicio, v.hora_fin
FROM valores v
JOIN mod05_tiposervicio t ON t.nombre = v.servicio
ON CONFLICT DO NOTHING;
