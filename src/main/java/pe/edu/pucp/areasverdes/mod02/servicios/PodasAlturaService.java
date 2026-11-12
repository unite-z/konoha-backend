package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.Intervencion;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.MotivoPodaAltura;
import pe.edu.pucp.areasverdes.mod02.servicios.IntervencionesPodaService.Prioridad;
import pe.edu.pucp.areasverdes.mod05.AtencionService;
import pe.edu.pucp.areasverdes.mod05.Entradas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Poda de altura a demanda vinculada al ejemplar (AV-312, RF-POD-005, HU-MOD02-024).
 *
 * <p>El jefe de sección la solicita con su motivo. En una sola transacción se crea la atención a
 * demanda en MOD-05 ({@link AtencionService}, el mismo servicio que usa MOD-05) y la intervención
 * {@code AlturaDemanda} del ejemplar, sin fecha de ejecución: la fecha y la foto se registran al
 * ejecutarse. Si MOD-05 rechaza la atención, no queda nada a medias.
 *
 * <ul>
 *   <li>RN-005: por debajo de {@value #ALTURA_MINIMA_M} m corresponde poda menor propia; se puede
 *       seguir por proveedor dejando la justificación.
 *   <li>RN-009: el motivo «riesgo» marca la atención como de riesgo, y MOD-05 exige la verificación
 *       de seguridad antes de iniciarla (AV-377).
 *   <li>«Planta muerta» se registra como motivo; proponer el cambio de situación del ejemplar llega
 *       con AV-303, que crea la situación.
 * </ul>
 */
@Service
public class PodasAlturaService {

    /** Altura desde la que la poda corresponde al proveedor (RN-005). */
    public static final int ALTURA_MINIMA_M = 5;

    private static final String ENTIDAD = "mod02_intervencion_arbolado";

    private static final Map<MotivoPodaAltura, String> TEXTO_MOTIVO =
            Map.of(
                    MotivoPodaAltura.Riesgo, "riesgo",
                    MotivoPodaAltura.Obstruccion, "obstrucción",
                    MotivoPodaAltura.PlantaMuerta, "planta muerta",
                    MotivoPodaAltura.Reduccion, "reducción de tamaño o de ramas");

    private final NamedParameterJdbcTemplate jdbc;
    private final IntervencionesPodaService intervenciones;
    private final AtencionService atenciones;
    private final RegistradorAuditoria auditoria;
    private final Clock reloj;

    public PodasAlturaService(
            NamedParameterJdbcTemplate jdbc,
            IntervencionesPodaService intervenciones,
            AtencionService atenciones,
            RegistradorAuditoria auditoria,
            Clock reloj) {
        this.jdbc = jdbc;
        this.intervenciones = intervenciones;
        this.atenciones = atenciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public record NuevaPodaAltura(
            @NotNull(message = "Indica el ejemplar") Long ejemplarId,
            @NotNull(message = "Indica el motivo") MotivoPodaAltura motivo,
            @NotNull(message = "Indica el contrato del proveedor") Long contratoId,
            @NotBlank(message = "Describe lo que hay que hacer")
                    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
                    String descripcion,
            Long tipoIntervencionId,
            Prioridad prioridad,
            Long incidenciaId,
            @Size(max = 300, message = "La justificación no puede superar 300 caracteres")
                    String justificacion) {}

    /** Contrato vigente de un servicio tercerizado, para elegir el proveedor. */
    public record ContratoPoda(
            long id,
            String numero,
            String proveedor,
            String servicio,
            boolean esRiesgo,
            LocalDate vigenciaFin) {}

    /** Datos del ejemplar que necesita el formulario: altura y si corresponde al proveedor. */
    public record EjemplarPoda(
            long id,
            String codigo,
            String especieNombreComun,
            String ubicacion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal alturaTotalM,
            boolean requiereJustificacion) {}

    public List<ContratoPoda> contratos(Usuario usuario) {
        AccesoPoda.planificar(usuario);
        return jdbc.query(
                """
                SELECT c.id, c.numero, v.razon_social proveedor, t.nombre servicio, t.es_riesgo,
                    c.vigencia_fin
                FROM mod05_contrato c
                JOIN mod05_proveedor v ON v.id = c.proveedor_id
                JOIN mod05_tiposervicio t ON t.id = c.tipo_servicio_id
                WHERE v.activo AND t.activo AND t.ejecutor = 'Tercerizado'
                    AND :hoy BETWEEN c.vigencia_inicio AND c.vigencia_fin
                ORDER BY lower(t.nombre) LIKE '%poda%' DESC, v.razon_social, c.numero
                """,
                Map.of("hoy", LocalDate.now(reloj)), new DataClassRowMapper<>(ContratoPoda.class));
    }

    public EjemplarPoda ejemplar(long id, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        return buscarEjemplar(id);
    }

    @Transactional
    public Intervencion solicitar(NuevaPodaAltura entrada, Usuario usuario) {
        AccesoPoda.planificar(usuario);
        EjemplarPoda ejemplar = buscarEjemplar(entrada.ejemplarId());
        String justificacion = textoOpcional(entrada.justificacion());
        if (ejemplar.requiereJustificacion() && justificacion == null) {
            throw new ErrorCoberturas(
                    400,
                    "justificacion-obligatoria",
                    "El ejemplar mide menos de %d m: corresponde poda menor propia. Explica por qué va al proveedor"
                            .formatted(ALTURA_MINIMA_M));
        }
        if (entrada.tipoIntervencionId() != null) {
            validarTipo(entrada.tipoIntervencionId());
        }
        intervenciones.validarIncidencia(entrada.incidenciaId(), ejemplar.id());
        asegurarCuentaMod05(usuario);

        var atencion =
                atenciones.crear(
                        new Entradas.Crear(
                                entrada.contratoId(),
                                descripcionAtencion(ejemplar, entrada, justificacion),
                                "Demanda",
                                null,
                                null,
                                entrada.motivo() == MotivoPodaAltura.Riesgo ? Boolean.TRUE : null,
                                List.of(new Entradas.Alcance(null, ejemplar.id(), null, null)),
                                null,
                                null,
                                null),
                        usuario);

        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod02_intervencion_arbolado (ejemplar_id, categoria, atencion_id,
                            incidencia_id, tipo_intervencion_id, prioridad, motivo,
                            altura_referencia_m, justificacion, usuario_creacion_id,
                            usuario_modificacion_id)
                        VALUES (:ejemplar, 'AlturaDemanda', :atencion, :incidencia, :tipo,
                            :prioridad, :motivo, :altura, :justificacion, :usuario, :usuario)
                        RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("ejemplar", ejemplar.id())
                                .addValue("atencion", ((Number) atencion.get("id")).longValue())
                                .addValue("incidencia", entrada.incidenciaId(), Types.BIGINT)
                                .addValue("tipo", entrada.tipoIntervencionId(), Types.BIGINT)
                                .addValue(
                                        "prioridad",
                                        entrada.prioridad() == null
                                                ? null
                                                : entrada.prioridad().name(),
                                        Types.VARCHAR)
                                .addValue("motivo", entrada.motivo().name())
                                .addValue("altura", ejemplar.alturaTotalM(), Types.NUMERIC)
                                .addValue("justificacion", justificacion, Types.VARCHAR)
                                .addValue("usuario", usuario.id()),
                        Long.class);
        Intervencion creada = intervenciones.buscar(id);
        auditoria.registrarAlta(
                ENTIDAD, id, IntervencionesPodaService.valores(creada), usuario.id());
        return creada;
    }

    private EjemplarPoda buscarEjemplar(long id) {
        return jdbc
                .query(
                        """
                        SELECT e.id, e.codigo, s.nombre_comun especie_nombre_comun, e.ubicacion,
                            e.altura_total_m,
                            coalesce(e.altura_total_m < :minima, false) requiere_justificacion
                        FROM mod02_ejemplar e
                        LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                        WHERE e.id = :id
                        """,
                        Map.of("id", id, "minima", ALTURA_MINIMA_M),
                        new DataClassRowMapper<>(EjemplarPoda.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        400, "ejemplar-inexistente", "El ejemplar no existe"));
    }

    private void validarTipo(long tipoId) {
        Boolean activo =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM mod02_tipo_intervencion WHERE id = :id"
                                + " AND activo)",
                        Map.of("id", tipoId),
                        Boolean.class);
        if (!Boolean.TRUE.equals(activo)) {
            throw new ErrorCoberturas(
                    400, "tipo-invalido", "El tipo de intervención no existe o no está activo");
        }
    }

    /**
     * Las atenciones de MOD-05 apuntan a {@code mod05_usuario}: una cuenta creada solo en MOD-01 se
     * da de alta allí con el mismo id, como hace MOD-03 al crear sus requerimientos.
     */
    private void asegurarCuentaMod05(Usuario usuario) {
        int creadas =
                jdbc.update(
                        """
                        INSERT INTO mod05_usuario (id, usuario, nombre, password_hash, activo)
                        SELECT u.id, u.nombre_usuario, u.nombre, '!', true FROM core_usuario u
                        WHERE u.id = :id AND u.activo
                        ON CONFLICT DO NOTHING
                        """,
                        Map.of("id", usuario.id()));
        if (creadas > 0) {
            jdbc.queryForList(
                    "SELECT setval(pg_get_serial_sequence('mod05_usuario', 'id'),"
                            + " (SELECT max(id) FROM mod05_usuario))",
                    Map.of());
        }
    }

    private static String descripcionAtencion(
            EjemplarPoda ejemplar, NuevaPodaAltura entrada, String justificacion) {
        String altura =
                ejemplar.alturaTotalM() == null
                        ? "altura sin registrar"
                        : ejemplar.alturaTotalM().stripTrailingZeros().toPlainString() + " m";
        String especie =
                ejemplar.especieNombreComun() == null
                        ? "especie sin asignar"
                        : ejemplar.especieNombreComun();
        return "Poda de altura a demanda del ejemplar %s (%s, %s). Motivo: %s. %s%s (solicitada desde MOD-02)."
                .formatted(
                        ejemplar.codigo(),
                        especie,
                        altura,
                        TEXTO_MOTIVO.get(entrada.motivo()),
                        entrada.descripcion().strip(),
                        justificacion == null
                                ? ""
                                : " Por debajo de " + ALTURA_MINIMA_M + " m: " + justificacion);
    }

    private static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
