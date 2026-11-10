package pe.edu.pucp.areasverdes.mod06.ia.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.comun.BaseDePruebas;

/**
 * {@code mod06_sugerencia_ia} (V72) a través de {@link SugerenciasIaRepository}: alta, lectura con
 * los nombres de MOD-01, filtros del listado, revisión y su rastro en la auditoría común. Cada
 * prueba se revierte.
 */
@SpringBootTest
@Transactional
class SugerenciasIaRepositoryIntegrationTest {

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry p) {
        BaseDePruebas.registrar(p);
    }

    @Autowired SugerenciasIaRepository repositorio;
    @Autowired NamedParameterJdbcTemplate jdbc;

    private long usuario;

    @BeforeEach
    void crearUsuario() {
        usuario =
                jdbc.queryForObject(
                        """
                        -- Id explícito: otras suites crean usuarios con id y dejan atrás la secuencia.
                        INSERT INTO core_usuario (id, nombre_usuario, nombre, credencial_hash, rol_id)
                        SELECT (SELECT COALESCE(max(id), 0) + 1 FROM core_usuario), 'prueba-mod06-ia',
                               'Ana Revisora', 'x', id
                        FROM core_rol WHERE codigo = 'jefe-seccion'
                        RETURNING id
                        """,
                        Map.of(),
                        Long.class);
    }

    private Map<String, Object> datos(String tipo, long registro, String instante) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("tipo", tipo);
        d.put("entidad", "ReporteProveedor");
        d.put("registro_id", registro);
        d.put("archivo_id", 7L);
        d.put(
                "contenido",
                Map.of(
                        "version_contenido",
                        1L,
                        "propuesta",
                        Map.of("campo_1", Map.of("valor", 12.5))));
        d.put("fecha_creacion", instante);
        d.put("usuario_creacion_id", usuario);
        return d;
    }

    @Test
    void creaEnPropuestaConNombreYAuditoria() {
        Map<String, Object> s =
                repositorio.crear(datos("ExtraccionReporte", 1, "2026-11-10T09:00:00-05:00"));

        assertEquals("Propuesta", s.get("estado"));
        assertEquals("Ana Revisora", s.get("usuario_creacion_nombre"));
        assertEquals("2026-11-10T09:00:00-05:00", s.get("fecha_creacion"));
        assertNull(s.get("validador_id"));
        assertEquals(
                12.5,
                ((Map<?, ?>)
                                ((Map<?, ?>) ((Map<?, ?>) s.get("contenido")).get("propuesta"))
                                        .get("campo_1"))
                        .get("valor"));
        assertEquals(1, auditorias((Long) s.get("id"), "Alta"));
    }

    @Test
    void listaPorTipoEstadoYRegistroDeLaMasReciente() {
        Map<String, Object> antigua =
                repositorio.crear(datos("ExtraccionReporte", 1, "2026-11-01T09:00:00-05:00"));
        Map<String, Object> reciente =
                repositorio.crear(datos("ExtraccionReporte", 2, "2026-11-02T09:00:00-05:00"));
        repositorio.crear(datos("Priorizacion", 1, "2026-11-03T09:00:00-05:00"));

        List<Map<String, Object>> todas = repositorio.listar("ExtraccionReporte", null, null, null);
        assertEquals(
                List.of(reciente.get("id"), antigua.get("id")),
                todas.stream().map(m -> m.get("id")).toList());
        assertEquals(
                1, repositorio.listar("ExtraccionReporte", null, "ReporteProveedor", 2L).size());
        assertEquals(0, repositorio.listar("ExtraccionReporte", "Validada", null, null).size());
    }

    @Test
    void guardaLaRevisionYLaAudita() {
        long id =
                (Long)
                        repositorio
                                .crear(datos("ExtraccionReporte", 1, "2026-11-10T09:00:00-05:00"))
                                .get("id");
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("estado", "Rechazada");
        cambios.put("validador_id", usuario);
        cambios.put("fecha_validacion", "2026-11-10T10:00:00-05:00");
        cambios.put("contenido", Map.of("decision", Map.of("motivo_rechazo", "Informe ilegible")));
        cambios.put("fecha_modificacion", "2026-11-10T10:00:00-05:00");
        cambios.put("usuario_modificacion_id", usuario);

        Map<String, Object> revisada = repositorio.guardarRevision(id, cambios, "Informe ilegible");

        assertEquals("Rechazada", revisada.get("estado"));
        assertEquals("Ana Revisora", revisada.get("validador_nombre"));
        assertEquals(1, auditorias(id, "CambioEstado"));
        assertEquals("Rechazada", repositorio.obtenerParaRevisar(id).get("estado"));
    }

    @Test
    void laBaseExigeValidadorEnUnaSugerenciaRevisada() {
        long id =
                (Long)
                        repositorio
                                .crear(datos("ExtraccionReporte", 1, "2026-11-10T09:00:00-05:00"))
                                .get("id");
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE mod06_sugerencia_ia SET estado = 'Validada' WHERE id = :id",
                                Map.of("id", id)));
    }

    @Test
    void noExisteDevuelveNulo() {
        assertNull(repositorio.obtener(999_999_999L));
        assertNull(repositorio.obtenerParaRevisar(999_999_999L));
    }

    private int auditorias(long id, String accion) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM core_registro_auditoria
                WHERE entidad = :entidad AND registro_id = :id AND accion = :accion
                """,
                Map.of(
                        "entidad",
                        SugerenciasIaRepository.ENTIDAD_AUDITORIA,
                        "id",
                        id,
                        "accion",
                        accion),
                Integer.class);
    }
}
