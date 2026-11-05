package pe.edu.pucp.areasverdes.mod02.servicios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.api.Pagina;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.Avance;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.Capacidad;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.ClaseCampana;
import pe.edu.pucp.areasverdes.mod02.servicios.ReglasCampanaPoda.EstadoCampana;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Planificación de la campaña de poda mayor (AV-308, RF-POD-001, RN-011, RN-018). */
@Service
public class CampanasPodaService {

    private static final String ENTIDAD = "mod02_campana_poda";

    /** RN-018, por si MOD-03 no tiene configurada la duración de la poda mayor. */
    private static final int DURACION_MAXIMA_RN018 = 10;

    private static final String CONSULTA =
            """
            SELECT c.id, c.anio, c.nombre, c.clase, c.motivo_extraordinaria, c.fecha_inicio,
                c.fecha_fin, c.objetivo_diario, c.estado, c.atencion_id,
                (SELECT count(*) FROM mod02_campana_ejemplar l WHERE l.campana_id = c.id)
                    total_ejemplares,
                (SELECT count(DISTINCT i.ejemplar_id) FROM mod02_intervencion_arbolado i
                    WHERE i.campana_id = c.id AND i.activo AND %s) verificables,
                (SELECT count(DISTINCT i.ejemplar_id) FROM mod02_intervencion_arbolado i
                    WHERE i.campana_id = c.id AND i.activo) con_registro
            FROM mod02_campana_poda c
            WHERE c.activo
            """
                    .formatted(IntervencionesPodaService.TIENE_FOTO);

    private static final String CIERRES =
            """
            FROM mod03_restriccion_calendario r
            JOIN mod03_tipo_restriccion t ON t.id = r.tipo_restriccion_id
            WHERE t.nombre = 'Cierre del campus' AND r.activo AND r.area_verde_id IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;
    private final int umbralRendimiento;

    public CampanasPodaService(
            NamedParameterJdbcTemplate jdbc,
            RegistradorAuditoria auditoria,
            @Value("${mod02.poda.umbral-rendimiento:18}") int umbralRendimiento) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.umbralRendimiento = umbralRendimiento;
    }

    public record NuevaCampana(
            @NotBlank(message = "Indica el nombre de la campaña")
                    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
                    String nombre,
            ClaseCampana clase,
            @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
                    String motivoExtraordinaria,
            @NotNull(message = "Indica el inicio del periodo") LocalDate fechaInicio,
            @NotNull(message = "Indica el fin del periodo") LocalDate fechaFin,
            @NotNull(message = "Indica el objetivo diario")
                    @Positive(message = "El objetivo diario debe ser mayor que cero")
                    Integer objetivoDiario,
            @Size(max = 2000, message = "La lista no puede superar 2000 ejemplares")
                    List<Long> ejemplarIds,
            EstadoCampana estado) {}

    public record CambioCampana(
            @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres")
                    String nombre,
            @Size(max = 300, message = "El motivo no puede superar 300 caracteres")
                    String motivoExtraordinaria,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            @Positive(message = "El objetivo diario debe ser mayor que cero")
                    Integer objetivoDiario,
            @Size(max = 2000, message = "La lista no puede superar 2000 ejemplares")
                    List<Long> ejemplarIds) {}

    public record Campana(
            long id,
            int anio,
            String nombre,
            ClaseCampana clase,
            String motivoExtraordinaria,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int objetivoDiario,
            EstadoCampana estado,
            Long atencionId,
            long totalEjemplares,
            Capacidad capacidad,
            Avance avance) {}

    public record EjemplarCampana(
            long id,
            String codigo,
            String referenciaCatastro,
            String codigoPlacaAntigua,
            String especieNombreComun,
            String ubicacion,
            Long areaVerdeId,
            String estadoIntervencion) {}

    public record VentanaCierre(
            int anio, LocalDate fechaInicio, LocalDate fechaFin, int duracionMaximaDias) {}

    /** Fila de la campaña, sin los datos calculados. */
    public record FilaCampana(
            long id,
            int anio,
            String nombre,
            ClaseCampana clase,
            String motivoExtraordinaria,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int objetivoDiario,
            EstadoCampana estado,
            Long atencionId,
            long totalEjemplares,
            long verificables,
            long conRegistro) {

        Campana comoCampana() {
            return new Campana(
                    id,
                    anio,
                    nombre,
                    clase,
                    motivoExtraordinaria,
                    fechaInicio,
                    fechaFin,
                    objetivoDiario,
                    estado,
                    atencionId,
                    totalEjemplares,
                    ReglasCampanaPoda.capacidad(
                            fechaInicio, fechaFin, objetivoDiario, totalEjemplares),
                    ReglasCampanaPoda.avance(
                            verificables, conRegistro - verificables, totalEjemplares));
        }
    }

    public Pagina<Campana> listar(Integer anio, EstadoCampana estado, int page, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        validarPagina(page);
        var filtros =
                new MapSqlParameterSource()
                        .addValue("anio", anio, java.sql.Types.INTEGER)
                        .addValue(
                                "estado",
                                estado == null ? null : estado.name(),
                                java.sql.Types.VARCHAR)
                        .addValue("limite", Pagina.TAMANO)
                        .addValue("offset", (page - 1) * Pagina.TAMANO);
        String condicion =
                " AND (CAST(:anio AS integer) IS NULL OR c.anio = :anio)"
                        + " AND (CAST(:estado AS varchar) IS NULL OR c.estado = :estado)";
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM mod02_campana_poda c WHERE c.activo" + condicion,
                        filtros,
                        Long.class);
        List<Campana> campanas =
                jdbc
                        .query(
                                CONSULTA
                                        + condicion
                                        + " ORDER BY c.anio DESC, c.id DESC"
                                        + " LIMIT :limite OFFSET :offset",
                                filtros,
                                new DataClassRowMapper<>(FilaCampana.class))
                        .stream()
                        .map(FilaCampana::comoCampana)
                        .toList();
        return Pagina.conTotal(campanas, total, page);
    }

    public Campana detalle(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return buscar(id, false).comoCampana();
    }

    /** Rendimiento con las podas verificadas: ver {@link RendimientoCampana}. */
    public RendimientoCampana.Resultado rendimiento(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        buscar(id, false);
        List<RendimientoCampana.Registro> registros =
                jdbc.query(
                        """
                        SELECT i.ejemplar_id, i.fecha_ejecucion fecha
                        FROM mod02_intervencion_arbolado i
                        WHERE i.campana_id = :id AND i.activo AND i.fecha_ejecucion IS NOT NULL
                            AND %s
                        """
                                .formatted(IntervencionesPodaService.TIENE_FOTO),
                        Map.of("id", id),
                        new DataClassRowMapper<>(RendimientoCampana.Registro.class));
        return RendimientoCampana.calcular(registros, umbralRendimiento);
    }

    public Pagina<EjemplarCampana> ejemplares(long id, String q, int page, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        validarPagina(page);
        buscar(id, false);
        var filtros =
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("q", q == null ? "" : q.strip())
                        .addValue("limite", Pagina.TAMANO)
                        .addValue("offset", (page - 1) * Pagina.TAMANO);
        String desde =
                """
                FROM mod02_campana_ejemplar l
                JOIN mod02_ejemplar e ON e.id = l.ejemplar_id
                LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                WHERE l.campana_id = :id AND (:q = '' OR e.codigo ILIKE '%' || :q || '%'
                    OR e.referencia_catastro ILIKE '%' || :q || '%'
                    OR e.codigo_placa_antigua ILIKE '%' || :q || '%'
                    OR s.nombre_comun ILIKE '%' || :q || '%')
                """;
        long total = jdbc.queryForObject("SELECT count(*) " + desde, filtros, Long.class);
        List<EjemplarCampana> filas =
                jdbc.query(
                        """
                        SELECT e.id, e.codigo, e.referencia_catastro, e.codigo_placa_antigua,
                            s.nombre_comun especie_nombre_comun, e.ubicacion, e.area_verde_id,
                            CASE
                                WHEN EXISTS (SELECT 1 FROM mod02_intervencion_arbolado i
                                    WHERE i.campana_id = l.campana_id AND i.ejemplar_id = e.id
                                    AND i.activo AND %s) THEN 'Verificada'
                                WHEN EXISTS (SELECT 1 FROM mod02_intervencion_arbolado i
                                    WHERE i.campana_id = l.campana_id AND i.ejemplar_id = e.id
                                    AND i.activo) THEN 'Incompleta'
                                ELSE 'Pendiente'
                            END estado_intervencion
                        """
                                        .formatted(IntervencionesPodaService.TIENE_FOTO)
                                + desde
                                + " ORDER BY e.codigo LIMIT :limite OFFSET :offset",
                        filtros,
                        new DataClassRowMapper<>(EjemplarCampana.class));
        return Pagina.conTotal(filas, total, page);
    }

    public VentanaCierre ventanaCierre(int anio, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        record Periodo(LocalDate fechaInicio, LocalDate fechaFin) {}
        List<Periodo> periodos =
                jdbc.query(
                        "SELECT r.fecha_inicio, r.fecha_fin "
                                + CIERRES
                                + " AND EXTRACT(YEAR FROM r.fecha_inicio) = :anio"
                                + " ORDER BY r.fecha_inicio LIMIT 1",
                        Map.of("anio", anio),
                        new DataClassRowMapper<>(Periodo.class));
        if (periodos.isEmpty()) {
            throw cierreNoRegistrado(anio);
        }
        Periodo periodo = periodos.get(0);
        return new VentanaCierre(anio, periodo.fechaInicio(), periodo.fechaFin(), duracionMaxima());
    }

    @Transactional
    public Campana crear(NuevaCampana entrada, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        ClaseCampana clase = entrada.clase() == null ? ClaseCampana.Anual : entrada.clase();
        EstadoCampana estado = entrada.estado() == null ? EstadoCampana.Borrador : entrada.estado();
        if (!estado.editable()) {
            throw new ErrorCoberturas(
                    400,
                    "estado-invalido",
                    "Una campaña nueva solo puede guardarse o planificarse");
        }
        validarFechas(entrada.fechaInicio(), entrada.fechaFin());
        String motivo = motivoSegunClase(clase, entrada.motivoExtraordinaria());
        Set<Long> ejemplares = validarEjemplares(entrada.ejemplarIds());
        if (estado == EstadoCampana.Planificada) {
            validarPlanificacion(
                    clase, entrada.fechaInicio(), entrada.fechaFin(), ejemplares.size());
        }
        long id;
        try {
            id =
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod02_campana_poda (anio, nombre, clase,
                                motivo_extraordinaria, fecha_inicio, fecha_fin, objetivo_diario,
                                estado, usuario_creacion_id, usuario_modificacion_id)
                            VALUES (:anio, :nombre, :clase, :motivo, :inicio, :fin, :objetivo,
                                :estado, :usuario, :usuario)
                            RETURNING id
                            """,
                            new MapSqlParameterSource()
                                    .addValue("anio", entrada.fechaInicio().getYear())
                                    .addValue("nombre", entrada.nombre().strip())
                                    .addValue("clase", clase.name())
                                    .addValue("motivo", motivo, java.sql.Types.VARCHAR)
                                    .addValue("inicio", entrada.fechaInicio())
                                    .addValue("fin", entrada.fechaFin())
                                    .addValue("objetivo", entrada.objetivoDiario())
                                    .addValue("estado", estado.name())
                                    .addValue("usuario", usuario.id()),
                            Long.class);
        } catch (DuplicateKeyException e) {
            throw campanaAnualExistente(entrada.fechaInicio().getYear());
        }
        guardarEjemplares(id, ejemplares);
        FilaCampana creada = buscar(id, false);
        auditoria.registrarAlta(ENTIDAD, id, valores(creada), usuario.id());
        return creada.comoCampana();
    }

    @Transactional
    public Campana cambiar(long id, CambioCampana cambio, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        if (cambio.nombre() == null
                && cambio.motivoExtraordinaria() == null
                && cambio.fechaInicio() == null
                && cambio.fechaFin() == null
                && cambio.objetivoDiario() == null
                && cambio.ejemplarIds() == null) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        FilaCampana antes = buscar(id, true);
        if (!antes.estado().editable()) {
            throw transicionNoPermitida("Esta campaña ya no se puede modificar");
        }
        if (cambio.nombre() != null && cambio.nombre().isBlank()) {
            throw new ErrorCoberturas(400, "nombre-obligatorio", "Indica el nombre de la campaña");
        }
        LocalDate inicio =
                cambio.fechaInicio() == null ? antes.fechaInicio() : cambio.fechaInicio();
        LocalDate fin = cambio.fechaFin() == null ? antes.fechaFin() : cambio.fechaFin();
        validarFechas(inicio, fin);
        String motivo =
                motivoSegunClase(
                        antes.clase(),
                        cambio.motivoExtraordinaria() == null
                                ? antes.motivoExtraordinaria()
                                : cambio.motivoExtraordinaria());
        Set<Long> ejemplares =
                cambio.ejemplarIds() == null ? null : validarEjemplares(cambio.ejemplarIds());
        long total = ejemplares == null ? antes.totalEjemplares() : ejemplares.size();
        if (antes.estado() == EstadoCampana.Planificada) {
            validarPlanificacion(antes.clase(), inicio, fin, total);
        }
        try {
            jdbc.update(
                    """
                    UPDATE mod02_campana_poda SET anio = :anio, nombre = :nombre, fecha_inicio = :inicio,
                        fecha_fin = :fin, objetivo_diario = :objetivo,
                        motivo_extraordinaria = :motivo, fecha_modificacion = now(),
                        usuario_modificacion_id = :usuario
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("anio", inicio.getYear())
                            .addValue(
                                    "nombre",
                                    cambio.nombre() == null
                                            ? antes.nombre()
                                            : cambio.nombre().strip())
                            .addValue("inicio", inicio)
                            .addValue("fin", fin)
                            .addValue(
                                    "objetivo",
                                    cambio.objetivoDiario() == null
                                            ? antes.objetivoDiario()
                                            : cambio.objetivoDiario())
                            .addValue("motivo", motivo, java.sql.Types.VARCHAR)
                            .addValue("usuario", usuario.id()));
        } catch (DuplicateKeyException e) {
            throw campanaAnualExistente(inicio.getYear());
        }
        if (ejemplares != null) {
            jdbc.update(
                    "DELETE FROM mod02_campana_ejemplar WHERE campana_id = :id", Map.of("id", id));
            guardarEjemplares(id, ejemplares);
        }
        FilaCampana despues = buscar(id, false);
        auditoria.registrarCambio(
                ENTIDAD, id, "Modificacion", valores(antes), valores(despues), null, usuario.id());
        return despues.comoCampana();
    }

    @Transactional
    public void darDeBaja(long id, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        FilaCampana antes = buscar(id, true);
        if (!antes.estado().editable()) {
            throw transicionNoPermitida(
                    "Una campaña en ejecución o finalizada no se puede retirar");
        }
        jdbc.update(
                """
                UPDATE mod02_campana_poda SET activo = false, fecha_modificacion = now(),
                    usuario_modificacion_id = :usuario
                WHERE id = :id
                """,
                Map.of("id", id, "usuario", usuario.id()));
        auditoria.registrarCambio(
                ENTIDAD,
                id,
                "BajaLogica",
                valores(antes),
                Map.of("activo", false),
                null,
                usuario.id());
    }

    @Transactional
    public Campana planificar(long id, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        FilaCampana antes = buscar(id, true);
        exigirEstado(antes, EstadoCampana.Borrador, "Solo se planifica una campaña en borrador");
        validarPlanificacion(
                antes.clase(), antes.fechaInicio(), antes.fechaFin(), antes.totalEjemplares());
        return pasarA(antes, EstadoCampana.Planificada, usuario);
    }

    @Transactional
    public Campana iniciar(long id, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        FilaCampana antes = buscar(id, true);
        exigirEstado(antes, EstadoCampana.Planificada, "Solo se inicia una campaña planificada");
        return pasarA(antes, EstadoCampana.EnEjecucion, usuario);
    }

    @Transactional
    public Campana finalizar(long id, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        FilaCampana antes = buscar(id, true);
        exigirEstado(antes, EstadoCampana.EnEjecucion, "Solo se finaliza una campaña en ejecución");
        return pasarA(antes, EstadoCampana.Finalizada, usuario);
    }

    private Campana pasarA(FilaCampana antes, EstadoCampana estado, Usuario usuario) {
        jdbc.update(
                """
                UPDATE mod02_campana_poda SET estado = :estado, fecha_modificacion = now(),
                    usuario_modificacion_id = :usuario
                WHERE id = :id
                """,
                Map.of("id", antes.id(), "estado", estado.name(), "usuario", usuario.id()));
        FilaCampana despues = buscar(antes.id(), false);
        auditoria.registrarCambio(
                ENTIDAD,
                antes.id(),
                "CambioEstado",
                valores(antes),
                valores(despues),
                null,
                usuario.id());
        return despues.comoCampana();
    }

    private FilaCampana buscar(long id, boolean bloquear) {
        return jdbc
                .query(
                        CONSULTA + " AND c.id = :id" + (bloquear ? " FOR UPDATE OF c" : ""),
                        Map.of("id", id),
                        new DataClassRowMapper<>(FilaCampana.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        404, "campana-no-encontrada", "La campaña no existe"));
    }

    private void validarPlanificacion(
            ClaseCampana clase, LocalDate inicio, LocalDate fin, long totalEjemplares) {
        if (totalEjemplares == 0) {
            throw new ErrorCoberturas(
                    400, "campana-sin-ejemplares", "Selecciona al menos un ejemplar");
        }
        int maximo = duracionMaxima();
        if (ReglasCampanaPoda.dias(inicio, fin) > maximo) {
            throw new ErrorCoberturas(
                    400,
                    "campana-duracion-excedida",
                    "La campaña no puede durar más de " + maximo + " días");
        }
        if (clase == ClaseCampana.Anual) {
            validarDentroDelCierre(inicio, fin);
        }
    }

    private void validarDentroDelCierre(LocalDate inicio, LocalDate fin) {
        var periodo = new MapSqlParameterSource().addValue("inicio", inicio).addValue("fin", fin);
        Boolean dentro =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 "
                                + CIERRES
                                + " AND r.fecha_inicio <= :inicio AND r.fecha_fin >= :fin)",
                        periodo,
                        Boolean.class);
        if (Boolean.TRUE.equals(dentro)) {
            return;
        }
        Boolean registrado =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 "
                                + CIERRES
                                + " AND EXTRACT(YEAR FROM r.fecha_inicio) = :anio)",
                        Map.of("anio", inicio.getYear()),
                        Boolean.class);
        if (!Boolean.TRUE.equals(registrado)) {
            throw cierreNoRegistrado(inicio.getYear());
        }
        throw new ErrorCoberturas(
                400,
                "campana-fuera-de-cierre",
                "La campaña anual debe realizarse durante el cierre del campus");
    }

    /** Duración de MOD-03: la configurada o, si no hay, su valor inicial. */
    private int duracionMaxima() {
        Integer configurada =
                jdbc.getJdbcTemplate()
                        .queryForObject(
                                """
                                SELECT coalesce(
                                    (SELECT d.duracion_maxima_dias FROM mod03_duracion_servicio d
                                     WHERE d.tipo_servicio_id = t.id),
                                    (SELECT v.duracion_maxima_dias FROM mod03_valor_inicial_duracion v
                                     WHERE v.tipo_servicio_id = t.id))
                                FROM (SELECT
                                    (SELECT id FROM mod05_tiposervicio
                                     WHERE nombre = 'Poda mayor programada') id) t
                                """,
                                Integer.class);
        return configurada == null ? DURACION_MAXIMA_RN018 : configurada;
    }

    private Set<Long> validarEjemplares(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> unicos = new LinkedHashSet<>(ids);
        if (unicos.contains(null)) {
            throw ejemplarInexistente();
        }
        List<Long> existentes =
                jdbc.queryForList(
                        "SELECT id FROM mod02_ejemplar WHERE id IN (:ids)",
                        Map.of("ids", unicos),
                        Long.class);
        if (existentes.size() != unicos.size()) {
            throw ejemplarInexistente();
        }
        return unicos;
    }

    private void guardarEjemplares(long campana, Set<Long> ejemplares) {
        List<MapSqlParameterSource> filas = new ArrayList<>(ejemplares.size());
        for (Long ejemplar : ejemplares) {
            filas.add(
                    new MapSqlParameterSource()
                            .addValue("campana", campana)
                            .addValue("ejemplar", ejemplar));
        }
        jdbc.batchUpdate(
                "INSERT INTO mod02_campana_ejemplar (campana_id, ejemplar_id)"
                        + " VALUES (:campana, :ejemplar)",
                filas.toArray(new MapSqlParameterSource[0]));
    }

    private static String motivoSegunClase(ClaseCampana clase, String motivo) {
        if (clase != ClaseCampana.Extraordinaria) {
            return null;
        }
        if (motivo == null || motivo.isBlank()) {
            throw new ErrorCoberturas(
                    400, "motivo-obligatorio", "Indica el motivo de la campaña extraordinaria");
        }
        return motivo.strip();
    }

    private static void validarFechas(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) {
            throw new ErrorCoberturas(
                    400, "fechas-invalidas", "El fin no puede ser anterior al inicio");
        }
    }

    private static void validarPagina(int page) {
        if (page < 1) {
            throw new ErrorCoberturas(400, "pagina-invalida", "La página debe ser mayor que cero");
        }
    }

    private static void exigirEstado(FilaCampana campana, EstadoCampana estado, String detalle) {
        if (campana.estado() != estado) {
            throw transicionNoPermitida(detalle);
        }
    }

    private static Map<String, Object> valores(FilaCampana campana) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nombre", campana.nombre());
        valores.put("clase", campana.clase().name());
        valores.put("motivo_extraordinaria", campana.motivoExtraordinaria());
        valores.put("fecha_inicio", campana.fechaInicio().toString());
        valores.put("fecha_fin", campana.fechaFin().toString());
        valores.put("objetivo_diario", campana.objetivoDiario());
        valores.put("estado", campana.estado().name());
        valores.put("total_ejemplares", campana.totalEjemplares());
        return valores;
    }

    private static ErrorCoberturas transicionNoPermitida(String detalle) {
        return new ErrorCoberturas(409, "transicion-no-permitida", detalle);
    }

    private static ErrorCoberturas campanaAnualExistente(int anio) {
        return new ErrorCoberturas(
                409, "campana-anual-existente", "Ya existe una campaña anual en " + anio);
    }

    private static ErrorCoberturas cierreNoRegistrado(int anio) {
        return new ErrorCoberturas(
                409,
                "cierre-no-registrado",
                "Registra el cierre del campus de " + anio + " en el calendario de restricciones");
    }

    private static ErrorCoberturas ejemplarInexistente() {
        return new ErrorCoberturas(
                400, "ejemplar-inexistente", "La lista incluye un ejemplar que no existe");
    }
}
