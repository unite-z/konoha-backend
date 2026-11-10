package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/**
 * {@link FuenteDatosBD}: las correspondencias que no son directas entre las tablas reales y las
 * entidades que leen RF-REP-002, RF-REP-004 y RF-REP-006. Ids altos para no chocar con otros datos;
 * cada prueba se revierte.
 */
@SpringBootTest
@Transactional
class FuenteDatosBDIntegrationTest {

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired NamedParameterJdbcTemplate jdbc;

    private FuenteDatosBD fuente;

    @BeforeEach
    void escenario() {
        fuente = new FuenteDatosBD(jdbc);
        String[] sql = {
            """
            INSERT INTO core_usuario (id, nombre_usuario, nombre, credencial_hash, rol_id)
            SELECT 9001, 'capataz-9001', 'Capataz Prueba', 'x', id FROM core_rol WHERE codigo = 'capataz'
            """,
            "INSERT INTO mod02_sector (id, nombre, usuario_creacion_id, usuario_modificacion_id)"
                    + " VALUES (9001, 'Sector 9001', 9001, 9001)",
            """
            INSERT INTO mod02_area_verde (id, nombre, lugar, ubicacion, superficie_m2, usuario_creacion_id, usuario_modificacion_id)
            VALUES (9001, 'Jardín Prueba 9001', 'Campus', 'Frente al pabellón', 120, 9001, 9001),
                   (9002, 'Jardín Prueba 9002', 'Campus', 'Junto a la biblioteca', 80, 9001, 9001)
            """,
            "INSERT INTO mod02_historial_area_sector (area_verde_id, sector_id, usuario_id) VALUES"
                    + " (9001, 9001, 9001)",
            "INSERT INTO mod02_historial_capataz_sector (id, sector_id, capataz_id, usuario_id)"
                    + " VALUES (9001, 9001, 9001, 9001)",
            "INSERT INTO mod05_areaverde (id, nombre, activo) VALUES (9101, ' jardín prueba 9001',"
                    + " true)",
            "INSERT INTO mod05_areaverde (id, nombre, activo) VALUES (9102, 'Sin equivalente 9001',"
                    + " true)",
            "INSERT INTO mod04_personal (id, nombres, apellidos, tipo_personal, usuario_id) VALUES"
                    + " (9001, 'Rosa', 'Quispe', 'Capataz', 9001)",
            "INSERT INTO mod04_cuadrilla (id, nombre, capataz_id, usuario_creacion_id,"
                    + " usuario_modificacion_id) VALUES (9001, 'Cuadrilla 9001', 9001, 9001, 9001)",
            "INSERT INTO mod04_parte_diario (id, cuadrilla_id, capataz_id, fecha,"
                    + " usuario_creacion_id) VALUES (9001, 9001, 9001, '2026-10-05', 9001)",
            "INSERT INTO mod04_tipo_actividad (id, nombre, clase_id) VALUES (9001, 'Riego 9001',"
                    + " 1)",
            """
            INSERT INTO mod04_actividad (id, parte_id, tipo_actividad_id, area_verde_id, fecha_hora_actividad, tiempo_minutos, usuario_creacion_id)
            VALUES (9001, 9001, 9001, 9101, '2026-10-05T08:00:00-05:00', 90, 9001),
                   (9002, 9001, 9001, 9102, '2026-10-05T10:00:00-05:00', 30, 9001)
            """,
            "INSERT INTO mod05_tiposervicio (id, nombre, ejecutor, es_riesgo, activo) VALUES (9001,"
                    + " 'Corte 9001', 'Tercerizado', false, true)",
            """
            INSERT INTO mod03_programacion_servicio (id, codigo, tipo_servicio_id, estacion, fecha_inicio_prevista, fecha_fin_prevista,
                fecha_inicio_real, estado, creado_por_id, usuario_creacion_id, usuario_modificacion_id)
            VALUES (9001, 'CC-9001', 9001, 'Primavera', '2026-10-01', '2026-10-03', '2026-10-01', 'EnCurso', 9001, 9001, 9001),
                   (9002, 'CC-9002', 9001, 'Primavera', '2026-10-10', '2026-10-12', NULL, 'Cancelada', 9001, 9001, 9001)
            """,
            """
            INSERT INTO mod03_ruta_corte (programacion_id, area_verde_id, zona, orden, estado, marcado_en) VALUES
                (9001, 9001, 'Zona A', 1, 'Cortado', '2026-10-02T09:00:00-05:00'),
                (9001, 9002, 'Zona A', 2, 'Cortado', '2026-10-03T23:30:00-05:00'),
                (9002, 9001, 'Zona A', 1, 'Pendiente', NULL)
            """,
            "INSERT INTO mod03_limpieza_corte (programacion_id, fecha, usuario_id) VALUES (9001,"
                    + " '2026-10-04', 9001)",
        };
        for (String s : sql) jdbc.update(s, Map.of());
    }

    private Map<String, Object> registro(String entidad, long id) {
        return fuente.listar(entidad).stream()
                .filter(r -> Long.valueOf(id).equals(r.get("id")))
                .findFirst()
                .orElse(null);
    }

    @Test
    void elCicloDeCorteSaleDeLaProgramacionYSuRuta() {
        Map<String, Object> ciclo = registro("CicloCorte", 9001);
        assertEquals("En limpieza", ciclo.get("estado"));
        assertEquals("2026-10-01", ciclo.get("fecha_inicio_real"));
        // Último jardín marcado a las 23:30 en Lima: el fin de corte es ese día local, no el de
        // UTC.
        assertEquals("2026-10-03", ciclo.get("fecha_fin_corte"));
        assertEquals("2026-10-04", ciclo.get("fecha_inicio_limpieza"));
        assertNull(ciclo.get("fecha_fin_limpieza"));
        assertEquals("Ruta CC-9001", registro("RutaCorte", 9001).get("nombre"));
        // Las canceladas no son ciclos.
        assertNull(registro("CicloCorte", 9002));
    }

    @Test
    void laActividadSeUbicaEnElAreaDeInventarioPorNombre() {
        assertEquals(9001L, registro("Actividad", 9001).get("area_verde_id"));
        assertNull(registro("Actividad", 9002).get("area_verde_id"));
        assertEquals(90L, registro("Actividad", 9001).get("tiempo_minutos"));
    }

    @Test
    void elAreaTraeSuSectorYElSectorSuCapatazDeMod04() {
        assertEquals(9001L, registro("AreaVerde", 9001).get("sector_id"));
        assertEquals(9001L, registro("AsignacionSector", 9001).get("capataz_id"));
        assertNull(registro("AsignacionSector", 9001).get("fecha_fin"));
        Map<String, Object> capataz = registro("Personal", 9001);
        assertEquals(
                List.of("Rosa", "Quispe"),
                List.of(capataz.get("nombres"), capataz.get("apellidos")));
    }
}
