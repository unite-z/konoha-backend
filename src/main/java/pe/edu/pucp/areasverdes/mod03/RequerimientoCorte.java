package pe.edu.pucp.areasverdes.mod03;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod05.AtencionService;
import pe.edu.pucp.areasverdes.mod05.Entradas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Requerimiento de MOD-05 de un ciclo de corte con proveedor (AV-263, CU-010 paso 7): una atención
 * tercerizada de origen {@code ProgramacionEstacional} con el contrato vigente del proveedor y los
 * jardines de la ruta. Lo crea {@link AtencionService}, el mismo servicio que usa MOD-05.
 *
 * <p>Si no se puede crear, el ciclo se programa igual y guarda el motivo: el proveedor no tiene
 * contrato vigente para el servicio, ningún jardín de la ruta está en el catálogo de MOD-05 o el
 * usuario no puede registrar atenciones. Las condiciones de {@link AtencionService#crear} se
 * comprueban antes de llamarlo, porque un error dentro de su transacción desharía también la
 * programación.
 *
 * <p>MOD-05 aún tiene su propia tabla de áreas ({@code mod05_areaverde}); los jardines de MOD-02 se
 * cruzan por el nombre, igual que en {@link Incidencias}, hasta que se consolide (deuda de V1).
 */
@Service
class RequerimientoCorte {

    /** Permiso de MOD-01 que corresponde al jefe de MOD-05, el único que registra atenciones. */
    static final String REGISTRAR_ATENCION = "SER.ATENCION.GESTIONAR";

    static final String SIN_CONTRATO = "El proveedor no tiene contrato vigente para este servicio";
    static final String SIN_JARDINES = "Ningún jardín de la ruta está en el catálogo de MOD-05";
    static final String SIN_PERMISO = "Tu usuario no puede registrar requerimientos en MOD-05";
    static final String SIN_CUENTA =
            "Tu cuenta aún no está dada de alta en Servicios tercerizados (MOD-05)";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NamedParameterJdbcTemplate jdbc;
    private final AtencionService atenciones;
    private final Clock reloj;

    RequerimientoCorte(NamedParameterJdbcTemplate jdbc, AtencionService atenciones, Clock reloj) {
        this.jdbc = jdbc;
        this.atenciones = atenciones;
        this.reloj = reloj;
    }

    /** Crea el requerimiento del ciclo y lo enlaza, o guarda por qué no se creó. */
    void generar(long programacion, UsuarioPlanificacion usuario) {
        var ciclo =
                jdbc.queryForMap(
                        """
                        SELECT p.codigo, p.tipo_servicio_id, p.proveedor_id,
                            p.fecha_inicio_prevista, p.fecha_fin_prevista, t.nombre servicio
                        FROM mod03_programacion_servicio p
                        JOIN mod05_tiposervicio t ON t.id = p.tipo_servicio_id
                        WHERE p.id = :id
                        """,
                        Map.of("id", programacion));
        String motivo = motivo(programacion, usuario, ciclo);
        if (motivo != null) {
            jdbc.update(
                    "UPDATE mod03_programacion_servicio SET motivo_sin_requerimiento = :motivo"
                            + " WHERE id = :id",
                    Map.of("motivo", motivo, "id", programacion));
            return;
        }
        long contrato = contrato(ciclo).getFirst();
        var alcances =
                jardines(programacion).stream()
                        .map(area -> new Entradas.Alcance(area, null, null, null))
                        .toList();
        String descripcion =
                "%s %s, del %s al %s, con %d %s de la ruta (programado en MOD-03)"
                        .formatted(
                                ciclo.get("servicio"),
                                ciclo.get("codigo"),
                                fecha(ciclo.get("fecha_inicio_prevista")),
                                fecha(ciclo.get("fecha_fin_prevista")),
                                alcances.size(),
                                alcances.size() == 1 ? "jardín" : "jardines");
        var atencion =
                atenciones.crear(
                        new Entradas.Crear(
                                contrato,
                                descripcion,
                                "ProgramacionEstacional",
                                null,
                                null,
                                null,
                                alcances),
                        new Usuario(
                                usuario.id(),
                                "",
                                usuario.nombre(),
                                "",
                                true,
                                List.of(Usuario.JEFE)));
        jdbc.update(
                "UPDATE mod03_programacion_servicio SET atencion_id = :atencion WHERE id = :id",
                Map.of("atencion", atencion.get("id"), "id", programacion));
    }

    private String motivo(long programacion, UsuarioPlanificacion usuario, Map<String, Object> c) {
        if (!usuario.tiene(REGISTRAR_ATENCION)) {
            return SIN_PERMISO;
        }
        if (!cuentaEnMod05(jdbc, usuario)) {
            return SIN_CUENTA;
        }
        if (contrato(c).isEmpty()) {
            return SIN_CONTRATO;
        }
        if (jardines(programacion).isEmpty()) {
            return SIN_JARDINES;
        }
        return null;
    }

    /**
     * MOD-05 aún guarda quién registra la atención en su copia de usuarios ({@code mod05_usuario},
     * AV-287). Una cuenta creada solo en MOD-01 no está ahí, y crear la atención fallaría al
     * confirmar la transacción y desharía también lo de MOD-03.
     */
    static boolean cuentaEnMod05(NamedParameterJdbcTemplate jdbc, UsuarioPlanificacion usuario) {
        return !jdbc.queryForList(
                        "SELECT 1 FROM mod05_usuario WHERE id = :id AND activo",
                        Map.of("id", usuario.id()),
                        Integer.class)
                .isEmpty();
    }

    /** El contrato vigente hoy del proveedor para el servicio, como lo exige MOD-05. */
    private List<Long> contrato(Map<String, Object> ciclo) {
        return jdbc.queryForList(
                """
                SELECT c.id FROM mod05_contrato c
                JOIN mod05_proveedor v ON v.id = c.proveedor_id AND v.activo
                JOIN mod05_tiposervicio t ON t.id = c.tipo_servicio_id
                    AND t.activo AND t.ejecutor = 'Tercerizado'
                WHERE c.proveedor_id = :proveedor AND c.tipo_servicio_id = :servicio
                    AND :hoy BETWEEN c.vigencia_inicio AND c.vigencia_fin
                ORDER BY c.vigencia_fin DESC, c.id DESC LIMIT 1
                """,
                new MapSqlParameterSource()
                        .addValue("proveedor", ciclo.get("proveedor_id"))
                        .addValue("servicio", ciclo.get("tipo_servicio_id"))
                        .addValue("hoy", LocalDate.now(reloj)),
                Long.class);
    }

    /**
     * Las áreas activas de MOD-05 con el mismo nombre que los jardines de la ruta, sin repetir
     * (MOD-05 rechaza un espacio repetido en la misma atención).
     */
    private List<Long> jardines(long programacion) {
        return jdbc.queryForList(
                """
                SELECT v.id FROM mod03_ruta_corte r
                JOIN mod02_area_verde a ON a.id = r.area_verde_id
                JOIN mod05_areaverde v ON lower(v.nombre) = lower(a.nombre) AND v.activo
                WHERE r.programacion_id = :id
                GROUP BY v.id ORDER BY min(r.orden), v.id
                """,
                Map.of("id", programacion),
                Long.class);
    }

    private static String fecha(Object valor) {
        LocalDate dia = valor instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) valor;
        return dia.format(FECHA);
    }
}
