package pe.edu.pucp.areasverdes.mod03;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod03.Programador.Bloqueo;
import pe.edu.pucp.areasverdes.mod03.RestriccionesController.Restriccion;

/**
 * Alta de programaciones (HU-MOD03-004, RN-002 y RN-003). Ante una advertencia exige justificación,
 * que queda como constancia; ante una prohibición, además, confirmación expresa. Sin ellas no
 * guarda.
 */
@Service
public class ProgramacionServicios {

    /**
     * Alta de una programación. {@code confirmar_prohibicion} ya no existe (RF-PLA-004 v6, AV-251):
     * si un cliente antiguo lo envía, se ignora y la prohibición sigue bloqueando. {@code
     * ventana_mes} ({@code AAAA-MM}) solo vale con proveedor: es el mes en que el tercerizado tiene
     * que ejecutar el servicio (AV-253); sin ella, el mes del inicio.
     */
    @JsonIgnoreProperties("confirmar_prohibicion")
    public record NuevaProgramacion(
            @NotNull(message = "Indica el servicio") Long tipoServicioId,
            Long proveedorId,
            @NotNull(message = "Indica el inicio") LocalDate fechaInicioPrevista,
            @NotNull(message = "Indica el fin") LocalDate fechaFinPrevista,
            @Size(max = 300, message = "Máximo 300 caracteres") String justificacion,
            @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "La ventana es un mes AAAA-MM")
                    String ventanaMes,
            Long cuadrillaId,
            @Size(max = 200, message = "Máximo 200 áreas") List<Long> areaVerdeIds,
            @Size(max = 50, message = "Máximo 50 zonas") List<String> zonas) {}

    private record Duracion(boolean ruidoso, Integer maxima, Integer limpieza) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final AlertasPlanificacion alertas;
    private final CorteCesped corte;
    private final Avisos avisos;
    private final RequerimientoCorte requerimiento;

    public ProgramacionServicios(
            NamedParameterJdbcTemplate jdbc,
            AlertasPlanificacion alertas,
            CorteCesped corte,
            Avisos avisos,
            RequerimientoCorte requerimiento) {
        this.jdbc = jdbc;
        this.alertas = alertas;
        this.corte = corte;
        this.avisos = avisos;
        this.requerimiento = requerimiento;
    }

    @Transactional
    public long crear(NuevaProgramacion nueva, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        LocalDate inicio = nueva.fechaInicioPrevista();
        LocalDate fin = nueva.fechaFinPrevista();
        exigirPeriodo(inicio, fin);
        Duracion servicio = servicio(nueva.tipoServicioId());
        String ventana = ventana(nueva.proveedorId(), nueva.ventanaMes(), inicio, fin);
        exigirCuadrillaYAreas(nueva);
        List<Restriccion> conflictos = conflictos(servicio, inicio, fin, nueva.justificacion());
        var datos =
                new MapSqlParameterSource()
                        .addValue("servicio", nueva.tipoServicioId())
                        .addValue("proveedor", nueva.proveedorId())
                        .addValue("estacion", Programador.estacion(inicio))
                        .addValue("inicio", inicio)
                        .addValue("fin", fin)
                        .addValue("usuario", usuario.id())
                        .addValue("ventana", ventana)
                        .addValue("cuadrilla", nueva.cuadrillaId());
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod03_programacion_servicio(tipo_servicio_id, proveedor_id, estacion,
                            fecha_inicio_prevista, fecha_fin_prevista, ventana_mes, cuadrilla_id,
                            creado_por_id, usuario_creacion_id, usuario_modificacion_id)
                        VALUES (:servicio, :proveedor, :estacion, :inicio, :fin, :ventana,
                            :cuadrilla, :usuario, :usuario, :usuario) RETURNING id
                        """,
                        datos,
                        Long.class);
        asignarCodigo(id, inicio);
        registrarTransicion(id, "Programada", usuario, null);
        for (Long area : areas(nueva)) {
            jdbc.update(
                    "INSERT INTO mod03_programacion_area(programacion_id, area_verde_id) VALUES"
                            + " (:id, :area)",
                    Map.of("id", id, "area", area));
        }
        // Corte de césped: la ruta de las zonas elegidas se copia en el ciclo (AV-259).
        if (nueva.zonas() != null) {
            corte.copiarRuta(id, nueva.zonas());
            // Con proveedor, su requerimiento en MOD-05 (AV-263, CU-010 paso 7).
            if (nueva.proveedorId() != null) {
                requerimiento.generar(id, usuario);
            }
        }
        alertas.atenderFrecuencia(nueva.tipoServicioId(), usuario);
        guardarConstancias(id, conflictos, nueva.justificacion(), usuario);
        return id;
    }

    /** Nuevas fechas de una programación aún no iniciada, con la justificación si hace falta. */
    public record Reprogramacion(
            @NotNull(message = "Indica el inicio") LocalDate fechaInicioPrevista,
            @NotNull(message = "Indica el fin") LocalDate fechaFinPrevista,
            @Size(max = 300, message = "Máximo 300 caracteres") String justificacion) {}

    private record Actual(
            long tipoServicioId,
            Long proveedorId,
            String ventanaMes,
            String estado,
            LocalDate fechaInicioPrevista,
            LocalDate fechaFinPrevista) {}

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * AV-362: cambia las fechas de una programación en estado Programada, con las mismas reglas que
     * el alta (periodo, duración máxima, ventana del proveedor, prohibiciones y avisos con
     * justificación). Queda en el historial y, si es de un tercerizado, se le avisa del traslado
     * (AV-264).
     */
    @Transactional
    public void reprogramar(long id, Reprogramacion nueva, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        List<Actual> filas =
                jdbc.query(
                        "SELECT tipo_servicio_id, proveedor_id, ventana_mes, estado,"
                                + " fecha_inicio_prevista, fecha_fin_prevista FROM"
                                + " mod03_programacion_servicio WHERE id = :id FOR UPDATE",
                        Map.of("id", id),
                        new DataClassRowMapper<>(Actual.class));
        if (filas.isEmpty()) {
            throw new ErrorPlanificacion(
                    404, "programacion-no-encontrada", "No existe esa programación");
        }
        Actual actual = filas.getFirst();
        if (!"Programada".equals(actual.estado())) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "Solo se puede reprogramar una programación en estado Programada");
        }
        LocalDate inicio = nueva.fechaInicioPrevista();
        LocalDate fin = nueva.fechaFinPrevista();
        exigirPeriodo(inicio, fin);
        ventana(actual.proveedorId(), actual.ventanaMes(), inicio, fin);
        List<Restriccion> conflictos =
                conflictos(servicio(actual.tipoServicioId()), inicio, fin, nueva.justificacion());
        jdbc.update(
                "UPDATE mod03_programacion_servicio SET fecha_inicio_prevista = :inicio,"
                        + " fecha_fin_prevista = :fin, estacion = :estacion, fecha_modificacion ="
                        + " now(), usuario_modificacion_id = :usuario WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("inicio", inicio)
                        .addValue("fin", fin)
                        .addValue("estacion", Programador.estacion(inicio))
                        .addValue("usuario", usuario.id()));
        guardarConstancias(id, conflictos, nueva.justificacion(), usuario);
        String fechas =
                "del %s al %s (antes, del %s al %s)"
                        .formatted(
                                DIA.format(inicio),
                                DIA.format(fin),
                                DIA.format(actual.fechaInicioPrevista()),
                                DIA.format(actual.fechaFinPrevista()));
        String motivo =
                "Reprogramada "
                        + fechas
                        + (nueva.justificacion() == null || nueva.justificacion().isBlank()
                                ? ""
                                : ": " + nueva.justificacion().trim());
        registrarTransicion(
                id,
                "Programada",
                usuario,
                motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
        if (actual.proveedorId() != null) {
            avisos.aProveedor(
                    actual.proveedorId(), "Servicio trasladado " + fechas, "programacion", id);
        }
    }

    private static void exigirPeriodo(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) {
            throw new ErrorPlanificacion(
                    400, "periodo-invalido", "El fin debe ser posterior al inicio");
        }
    }

    /** El servicio periódico activo con su duración máxima; 404 si no existe. */
    private Duracion servicio(long tipoServicioId) {
        List<Duracion> servicios =
                jdbc.query(
                        "SELECT t.es_ruidoso ruidoso, d.duracion_maxima_dias maxima,"
                            + " coalesce(d.dias_limpieza, 0) limpieza FROM mod05_tiposervicio t"
                            + " LEFT JOIN mod03_duracion_servicio d ON d.tipo_servicio_id = t.id"
                            + " WHERE t.id = :id AND t.activo AND t.es_periodico",
                        Map.of("id", tipoServicioId),
                        new DataClassRowMapper<>(Duracion.class));
        if (servicios.isEmpty()) {
            throw new ErrorPlanificacion(404, "servicio-no-encontrado", "No existe ese servicio");
        }
        return servicios.getFirst();
    }

    /**
     * Duración máxima (RN-002) y restricciones que afectan al periodo, que tienen que constar
     * (RN-003). Devuelve las restricciones con las que coincide.
     */
    private List<Restriccion> conflictos(
            Duracion servicio, LocalDate inicio, LocalDate fin, String justificacion) {
        if (servicio.maxima() == null) {
            throw new ErrorPlanificacion(
                    409, "configuracion-incompleta", "Configura antes la duración del servicio");
        }
        Programador.exigirDuracion(
                Programador.dias(inicio, fin), servicio.maxima(), servicio.limpieza());
        List<Restriccion> conflictos =
                jdbc
                        .query(
                                RestriccionesController.CONSULTA.formatted(
                                        "r.fecha_inicio <= :hasta AND r.fecha_fin >= :desde"),
                                Map.of("desde", inicio, "hasta", fin),
                                new DataClassRowMapper<>(Restriccion.class))
                        .stream()
                        .filter(r -> afecta(r, inicio, fin, servicio.ruidoso()))
                        .toList();
        exigirConstancia(conflictos, justificacion);
        return conflictos;
    }

    private void guardarConstancias(
            long id,
            List<Restriccion> conflictos,
            String justificacion,
            UsuarioPlanificacion usuario) {
        for (Restriccion r : conflictos) {
            jdbc.update(
                    "INSERT INTO mod03_confirmacion_restriccion(programacion_id, restriccion_id,"
                            + " usuario_id, justificacion) VALUES (:id, :restriccion, :usuario,"
                            + " :justificacion) ON CONFLICT (programacion_id, restriccion_id) DO"
                            + " NOTHING",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("restriccion", r.id())
                            .addValue("usuario", usuario.id())
                            .addValue("justificacion", justificacion));
        }
    }

    /**
     * Cambio de estado con su fecha real (hoy si no se indica): inicio, fin o cancelación. La
     * cancelación exige {@code motivo} (AV-253).
     */
    public record Cambio(
            LocalDate fecha, @Size(max = 300, message = "Máximo 300 caracteres") String motivo) {}

    private record Transicion(String desde, String hasta, String columna) {}

    private static final Map<String, Transicion> TRANSICIONES =
            Map.of(
                    "inicio", new Transicion("Programada", "EnCurso", "fecha_inicio_real"),
                    "fin", new Transicion("EnCurso", "Completada", "fecha_fin_real"),
                    "cancelacion", new Transicion("Programada", "Cancelada", null));

    /** Aplica la transición (inicio, fin o cancelación) si el estado actual lo permite. */
    @Transactional
    public void cambiar(
            long id, String accion, LocalDate fecha, String motivo, UsuarioPlanificacion usuario) {
        Acceso.gestionar(usuario);
        Transicion t = TRANSICIONES.get(accion);
        if ("Completada".equals(t.hasta())) {
            corte.exigirRutaCompleta(id);
        }
        boolean cancela = "Cancelada".equals(t.hasta());
        if (cancela && (motivo == null || motivo.isBlank())) {
            throw new ErrorPlanificacion(
                    400, "motivo-requerido", "Escribe el motivo de la cancelación");
        }
        String fechaReal =
                t.columna() == null
                        ? "motivo_cancelacion = :motivo, "
                        : t.columna() + " = :fecha, ";
        int filas =
                jdbc.update(
                        ("UPDATE mod03_programacion_servicio SET estado = :hasta, %s"
                                        + " fecha_modificacion = now(), usuario_modificacion_id ="
                                        + " :usuario WHERE id = :id AND estado = :desde")
                                .formatted(fechaReal),
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("desde", t.desde())
                                .addValue("hasta", t.hasta())
                                .addValue("fecha", fecha)
                                .addValue("motivo", cancela ? motivo.trim() : null)
                                .addValue("usuario", usuario.id()));
        if (filas == 0) {
            throw new ErrorPlanificacion(
                    409,
                    "transicion-invalida",
                    "Solo se puede registrar %s de una programación en estado %s"
                            .formatted(accion, t.desde()));
        }
        registrarTransicion(id, t.hasta(), usuario, cancela ? motivo.trim() : null);
    }

    /**
     * AV-364: código legible del ciclo, como CC-2026-11 (iniciales del servicio, año del inicio y
     * correlativo). El bloqueo serializa las altas simultáneas para que el número no se repita.
     */
    private void asignarCodigo(long id, LocalDate inicio) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtext('mod03_codigo_ciclo'))", Map.of(), rs -> {});
        jdbc.update(
                """
                UPDATE mod03_programacion_servicio p SET codigo = c.prefijo || '-' || :anio || '-'
                    || lpad((SELECT count(*) + 1 FROM mod03_programacion_servicio o
                        WHERE o.codigo LIKE c.prefijo || '-' || :anio || '-%')::text, 2, '0')
                FROM (SELECT mod03_prefijo_servicio(t.nombre) prefijo FROM mod05_tiposervicio t
                    JOIN mod03_programacion_servicio q ON q.tipo_servicio_id = t.id
                    WHERE q.id = :id) c
                WHERE p.id = :id
                """,
                Map.of("id", id, "anio", String.valueOf(inicio.getYear())));
    }

    private void registrarTransicion(
            long id, String estado, UsuarioPlanificacion usuario, String motivo) {
        jdbc.update(
                "INSERT INTO mod03_historial_programacion(programacion_id, estado, usuario_id,"
                        + " motivo) VALUES (:id, :estado, :usuario, :motivo)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("estado", estado)
                        .addValue("usuario", usuario.id())
                        .addValue("motivo", motivo));
    }

    private static List<Long> areas(NuevaProgramacion nueva) {
        return nueva.areaVerdeIds() == null
                ? List.of()
                : nueva.areaVerdeIds().stream().distinct().toList();
    }

    /**
     * La cuadrilla (MOD-04) solo con personal propio, activa; las áreas verdes (MOD-02), activas
     * (AV-254).
     */
    private void exigirCuadrillaYAreas(NuevaProgramacion nueva) {
        if (nueva.cuadrillaId() != null) {
            if (nueva.proveedorId() != null) {
                throw new ErrorPlanificacion(
                        400,
                        "cuadrilla-con-proveedor",
                        "Un servicio tercerizado no lleva cuadrilla propia");
            }
            if (jdbc.queryForObject(
                            "SELECT count(*) FROM mod04_cuadrilla WHERE id = :id AND activa",
                            Map.of("id", nueva.cuadrillaId()),
                            Integer.class)
                    == 0) {
                throw new ErrorPlanificacion(400, "cuadrilla-invalida", "No existe esa cuadrilla");
            }
        }
        List<Long> areas = areas(nueva);
        if (!areas.isEmpty()
                && jdbc.queryForObject(
                                "SELECT count(*) FROM mod02_area_verde WHERE id IN (:ids) AND"
                                        + " activo",
                                Map.of("ids", areas),
                                Integer.class)
                        != areas.size()) {
            throw new ErrorPlanificacion(400, "area-invalida", "Alguna área verde no existe");
        }
    }

    /**
     * Ventana mensual de un tercerizado (RF-PLA-004): la indicada o el mes del inicio, y el inicio
     * y el fin tienen que caer en ella. Con personal propio no hay ventana.
     */
    private static String ventana(
            Long proveedorId, String ventanaMes, LocalDate inicio, LocalDate fin) {
        if (proveedorId == null) {
            return null;
        }
        YearMonth mes = ventanaMes == null ? YearMonth.from(inicio) : YearMonth.parse(ventanaMes);
        if (!YearMonth.from(inicio).equals(mes) || !YearMonth.from(fin).equals(mes)) {
            throw new ErrorPlanificacion(
                    400,
                    "fuera-de-ventana",
                    "Las fechas tienen que caer en la ventana del proveedor (" + mes + ")");
        }
        return mes.toString();
    }

    private static boolean afecta(Restriccion r, LocalDate inicio, LocalDate fin, boolean ruidoso) {
        return !Programador.conflictos(List.of(Bloqueo.de(r)), inicio, fin, ruidoso).isEmpty();
    }

    /**
     * RF-PLA-004 v6: una prohibición impide guardar la programación, sin confirmación posible
     * (escenario «Prohibición por examen de admisión»); un aviso se guarda con motivo (escenario
     * «Aviso por actividad extracurricular»), que queda como constancia.
     */
    private static void exigirConstancia(List<Restriccion> conflictos, String justificacion) {
        List<Restriccion> prohibiciones =
                conflictos.stream().filter(r -> "Prohibicion".equals(r.efecto())).toList();
        if (!prohibiciones.isEmpty()) {
            throw new ErrorPlanificacion(
                    409,
                    "fecha-prohibida",
                    "Coincide con "
                            + motivos(prohibiciones)
                            + ", que prohíbe programar en esas fechas. Elige otras");
        }
        if (!conflictos.isEmpty() && (justificacion == null || justificacion.isBlank())) {
            throw new ErrorPlanificacion(
                    409,
                    "restricciones-sin-justificar",
                    "Coincide con " + motivos(conflictos) + ". Justifica la fecha o elige otra");
        }
    }

    private static String motivos(List<Restriccion> restricciones) {
        return String.join(
                ", ",
                restricciones.stream().map(r -> r.tipo() + " el " + r.fechaInicio()).toList());
    }
}
