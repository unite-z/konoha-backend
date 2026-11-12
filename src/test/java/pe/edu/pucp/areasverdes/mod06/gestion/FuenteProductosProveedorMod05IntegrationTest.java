package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteProductosProveedorMod05;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProductoAplicado;

/**
 * {@link FuenteProductosProveedorMod05}: los productos aplicados se leen de las tablas de MOD-05
 * con el proveedor del contrato, la fecha de inicio en America/Lima y el espacio del alcance. Ids
 * altos para no chocar con otros datos; cada prueba se revierte.
 *
 * <p>Hereda de {@link ApiGestion} para reutilizar su contexto de Spring (y su pool de 2
 * conexiones): un contexto propio suma conexiones y en CI agota las de PostgreSQL ("too many
 * clients already").
 */
@Transactional
class FuenteProductosProveedorMod05IntegrationTest extends ApiGestion {

    static final RangoFechas OCTUBRE =
            new RangoFechas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

    private FuenteProductosProveedorMod05 fuente;

    @BeforeEach
    void escenario() {
        fuente = new FuenteProductosProveedorMod05(jdbc);
        String[] sql = {
            "INSERT INTO mod05_usuario (id, usuario, nombre, password_hash) VALUES (9301,"
                    + " 'autor-9301', 'Autor 9301', 'x')",
            "INSERT INTO mod05_proveedor (id, razon_social, activo) VALUES (9301, 'Agro 9301',"
                    + " true)",
            "INSERT INTO mod05_tiposervicio (id, nombre, ejecutor, es_riesgo, activo) VALUES (9301,"
                    + " 'Fitosanitario 9301', 'Tercerizado', false, true)",
            "INSERT INTO mod05_contrato (id, numero, vigencia_inicio, vigencia_fin, proveedor_id,"
                    + " tipo_servicio_id) VALUES (9301, 'CT-9301', '2026-01-01', '2026-12-31',"
                    + " 9301, 9301)",
            "INSERT INTO mod05_areaverde (id, nombre, activo) VALUES (9301, 'Jardín 9301', true),"
                    + " (9302, 'Jardín 9302', true)",
            "INSERT INTO mod02_ejemplar (id, codigo, area_verde_id) VALUES (9301, 'EJ-9301', 9302)",
            """
            INSERT INTO mod05_atenciontercerizada (id, codigo, contrato_id, tipo_servicio_id, clase, descripcion, origen,
                numero_orden_compra, fecha_hora_inicio, estado, es_riesgo, comentarios_acuerdo_unidad,
                solicitado_por_id, fecha_creacion, fecha_modificacion, usuario_creacion_id, usuario_modificacion_id)
            VALUES
              (9301, 'AT-9301', 9301, 9301, 'ProgramadaContrato', '', 'Demanda', '', '2026-10-08T07:00:00-05:00',
               'Conforme / cerrado', false, '', 9301, now(), now(), 9301, 9301),
              (9302, 'AT-9302', 9301, 9301, 'ProgramadaContrato', '', 'Demanda', '', '2026-10-31T23:30:00-05:00',
               'Reporte adjunto', false, '', 9301, now(), now(), 9301, 9301),
              (9303, 'AT-9303', 9301, 9301, 'ProgramadaContrato', '', 'Demanda', '', '2026-10-15T07:00:00-05:00',
               'OC referenciada', false, '', 9301, now(), now(), 9301, 9301),
              (9304, 'AT-9304', 9301, 9301, 'ProgramadaContrato', '', 'Demanda', '', NULL,
               'En ejecución', false, '', 9301, now(), now(), 9301, 9301),
              (9305, 'AT-9305', 9301, 9301, 'ProgramadaContrato', '', 'Demanda', '', '2026-11-01T00:30:00-05:00',
               'Conforme / cerrado', false, '', 9301, now(), now(), 9301, 9301)
            """,
            "INSERT INTO mod05_alcanceatencion (atencion_id, area_verde_id) VALUES (9301, 9301)",
            "INSERT INTO mod05_alcanceatencion (atencion_id, ejemplar_id) VALUES (9302, 9301)",
            "INSERT INTO mod05_producto_agronomico (id, nombre, principio_activo) VALUES (9301,"
                    + " 'Cupravit 9301', 'Oxicloruro de cobre')",
            """
            INSERT INTO mod05_atencion_producto (atencion_id, producto_id, dosis)
            VALUES (9301, 9301, '2 g/L'), (9302, 9301, ''), (9303, 9301, '1 g/L'),
                   (9304, 9301, '1 g/L'), (9305, 9301, '1 g/L')
            """,
        };
        for (String s : sql) jdbc.getJdbcTemplate().update(s);
    }

    /** Solo las filas de este escenario, por si la base de pruebas trae otras. */
    private List<ProductoAplicado> delEscenario(Long area) {
        return fuente.aplicaciones(OCTUBRE, area).stream()
                .filter(a -> a.atencionCodigo().startsWith("AT-930"))
                .toList();
    }

    @Test
    void soloCuentanLasAtencionesIniciadasEnElMesConElProveedorDelContrato() {
        List<ProductoAplicado> r = delEscenario(null);
        assertEquals(
                List.of("AT-9301", "AT-9302"),
                r.stream().map(ProductoAplicado::atencionCodigo).toList());
        ProductoAplicado primero = r.getFirst();
        assertEquals(LocalDate.of(2026, 10, 8), primero.fecha());
        assertEquals("Cupravit 9301", primero.producto());
        assertEquals("Oxicloruro de cobre", primero.principioActivo());
        assertEquals("2 g/L", primero.dosis());
        assertEquals(9301, primero.proveedorId());
        assertEquals("Agro 9301", primero.proveedor());
        assertEquals(
                LocalDate.of(2026, 10, 31), r.get(1).fecha(), "El día se toma en America/Lima");
    }

    @Test
    void elEspacioCuentaPorAlcanceDirectoOPorElAreaDelEjemplar() {
        assertEquals(
                List.of("AT-9301"),
                delEscenario(9301L).stream().map(ProductoAplicado::atencionCodigo).toList());
        assertEquals(
                List.of("AT-9302"),
                delEscenario(9302L).stream().map(ProductoAplicado::atencionCodigo).toList());
        assertTrue(fuente.aplicaciones(OCTUBRE, 9399L).isEmpty());
    }
}
