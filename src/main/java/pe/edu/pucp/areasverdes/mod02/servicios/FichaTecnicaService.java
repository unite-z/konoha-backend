package pe.edu.pucp.areasverdes.mod02.servicios;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/**
 * Ficha técnica del ejemplar (AV-133, RF-INV-005): datos dasométricos, origen, estado de salud y
 * evaluaciones fitosanitarias y estructurales (HU-MOD02-011 y HU-MOD02-012).
 *
 * <p>Código, placa, situación e historial son de la ficha de {@code EjemplaresService} (RF-INV-006
 * y 007, AV-303); esta parte no los toca. Sin ninguna medida, el ejemplar queda pendiente de medir.
 * Una evaluación no se edita: una corrección es otra evaluación.
 */
@Service
public class FichaTecnicaService {

    private static final String ENTIDAD_EJEMPLAR = "mod02_ejemplar";
    private static final String ENTIDAD_EVALUACION = "mod02_evaluacion_ejemplar";

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;
    private final Clock reloj;

    public FichaTecnicaService(
            NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria, Clock reloj) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public enum Origen {
        Nativo,
        Introducido,
        Desconocido
    }

    public enum EstadoSalud {
        Bueno,
        Regular,
        Malo
    }

    /** Datos que el formulario cambia; un campo nulo no se toca, salvo que se pida vaciarlo. */
    public record CambioFicha(
            @DecimalMin(value = "0.01", message = "La altura total debe ser mayor que cero")
                    @DecimalMax(value = "150", message = "La altura total no puede pasar de 150 m")
                    BigDecimal alturaTotalM,
            @DecimalMin(value = "0.01", message = "La altura del fuste debe ser mayor que cero")
                    @DecimalMax(
                            value = "150",
                            message = "La altura del fuste no puede pasar de 150 m")
                    BigDecimal alturaFusteM,
            @DecimalMin(value = "0.1", message = "El DAP debe ser mayor que cero")
                    @DecimalMax(value = "1000", message = "El DAP no puede pasar de 1000 cm")
                    BigDecimal dapCm,
            @DecimalMin(value = "0.01", message = "El radio de copa debe ser mayor que cero")
                    @DecimalMax(value = "100", message = "El radio de copa no puede pasar de 100 m")
                    BigDecimal radioCopaM,
            Origen origen,
            EstadoSalud estadoSalud,
            /**
             * Medidas que se vacían a propósito: altura_total_m, altura_fuste_m, dap_cm,
             * radio_copa_m.
             */
            List<String> vaciar) {}

    public record NuevaEvaluacion(
            @NotNull(message = "Indica la fecha de la evaluación") LocalDate fecha,
            /** Ausencia explícita de hallazgos; sin el campo, falso. */
            Boolean sinHallazgos,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String enfermedadesPlagas,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String danosMecanicosZunchado,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String inclinacionRamasSecas,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String cavidadesPudricion,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String raicesExpuestas,
            @Size(max = 300, message = "Cada hallazgo admite hasta 300 caracteres")
                    String interferenciaInfraestructura,
            @Size(max = 500, message = "El manejo recomendado admite hasta 500 caracteres")
                    String manejoRecomendado) {}

    public record Evaluacion(
            long id,
            LocalDate fecha,
            boolean sinHallazgos,
            String enfermedadesPlagas,
            String danosMecanicosZunchado,
            String inclinacionRamasSecas,
            String cavidadesPudricion,
            String raicesExpuestas,
            String interferenciaInfraestructura,
            String manejoRecomendado,
            String autor,
            OffsetDateTime fechaCreacion) {}

    /** Ficha técnica con las evaluaciones, la más reciente primero. */
    public record Ficha(
            long id,
            String codigo,
            String especieNombreComun,
            String especieNombreCientifico,
            String especieFormaBiologica,
            String areaVerdeNombre,
            String cuartelNombre,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal alturaTotalM,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal alturaFusteM,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal dapCm,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal radioCopaM,
            Origen origen,
            EstadoSalud estadoSalud,
            boolean pendienteMedir,
            List<Evaluacion> evaluaciones) {}

    private record FilaFicha(
            long id,
            String codigo,
            String especieNombreComun,
            String especieNombreCientifico,
            String especieFormaBiologica,
            String areaVerdeNombre,
            String cuartelNombre,
            BigDecimal alturaTotalM,
            BigDecimal alturaFusteM,
            BigDecimal dapCm,
            BigDecimal radioCopaM,
            Origen origen,
            EstadoSalud estadoSalud) {}

    private static final List<String> MEDIDAS =
            List.of("altura_total_m", "altura_fuste_m", "dap_cm", "radio_copa_m");

    public Ficha ficha(long ejemplarId, Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        FilaFicha f = fila(ejemplarId);
        List<Evaluacion> evaluaciones =
                jdbc.query(
                        """
                        SELECT v.id, v.fecha, v.sin_hallazgos, v.enfermedades_plagas,
                            v.danos_mecanicos_zunchado, v.inclinacion_ramas_secas,
                            v.cavidades_pudricion, v.raices_expuestas,
                            v.interferencia_infraestructura, v.manejo_recomendado,
                            u.nombre autor, v.fecha_creacion
                        FROM mod02_evaluacion_ejemplar v
                        JOIN core_usuario u ON u.id = v.usuario_id
                        WHERE v.ejemplar_id = :id
                        ORDER BY v.fecha DESC, v.id DESC
                        """,
                        Map.of("id", ejemplarId),
                        new DataClassRowMapper<>(Evaluacion.class));
        boolean pendiente =
                Stream.of(f.alturaTotalM(), f.alturaFusteM(), f.dapCm(), f.radioCopaM())
                        .allMatch(valor -> valor == null);
        return new Ficha(
                f.id(),
                f.codigo(),
                f.especieNombreComun(),
                f.especieNombreCientifico(),
                f.especieFormaBiologica(),
                f.areaVerdeNombre(),
                f.cuartelNombre(),
                f.alturaTotalM(),
                f.alturaFusteM(),
                f.dapCm(),
                f.radioCopaM(),
                f.origen(),
                f.estadoSalud(),
                pendiente,
                evaluaciones);
    }

    @Transactional
    public Ficha cambiar(long ejemplarId, CambioFicha cambio, Usuario usuario) {
        exigirRegistro(usuario);
        List<String> vaciar = cambio.vaciar() == null ? List.of() : cambio.vaciar();
        for (String campo : vaciar) {
            if (!MEDIDAS.contains(campo)) {
                throw new ErrorCoberturas(
                        400, "campo-invalido", "Solo se pueden vaciar las medidas: " + campo);
            }
        }
        if (cambio.alturaTotalM() == null
                && cambio.alturaFusteM() == null
                && cambio.dapCm() == null
                && cambio.radioCopaM() == null
                && cambio.origen() == null
                && cambio.estadoSalud() == null
                && vaciar.isEmpty()) {
            throw new ErrorCoberturas(
                    400, "cambio-vacio", "Indica algún dato que quieras modificar");
        }
        jdbc.query(
                "SELECT id FROM mod02_ejemplar WHERE id = :id FOR UPDATE",
                Map.of("id", ejemplarId),
                (fila) -> {});
        FilaFicha antes = fila(ejemplarId);
        BigDecimal total =
                valor("altura_total_m", cambio.alturaTotalM(), antes.alturaTotalM(), vaciar);
        BigDecimal fuste =
                valor("altura_fuste_m", cambio.alturaFusteM(), antes.alturaFusteM(), vaciar);
        if (total != null && fuste != null && fuste.compareTo(total) > 0) {
            throw new ErrorCoberturas(
                    400,
                    "fuste-mayor-que-total",
                    "La altura del fuste no puede ser mayor que la altura total");
        }
        jdbc.update(
                """
                UPDATE mod02_ejemplar SET altura_total_m = :total, altura_fuste_m = :fuste,
                    dap_cm = :dap, radio_copa_m = :radio, origen = :origen,
                    estado_salud = :salud
                WHERE id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id", ejemplarId)
                        .addValue("total", total, Types.NUMERIC)
                        .addValue("fuste", fuste, Types.NUMERIC)
                        .addValue(
                                "dap",
                                valor("dap_cm", cambio.dapCm(), antes.dapCm(), vaciar),
                                Types.NUMERIC)
                        .addValue(
                                "radio",
                                valor(
                                        "radio_copa_m",
                                        cambio.radioCopaM(),
                                        antes.radioCopaM(),
                                        vaciar),
                                Types.NUMERIC)
                        .addValue(
                                "origen",
                                nombre(cambio.origen() != null ? cambio.origen() : antes.origen()),
                                Types.VARCHAR)
                        .addValue(
                                "salud",
                                nombre(
                                        cambio.estadoSalud() != null
                                                ? cambio.estadoSalud()
                                                : antes.estadoSalud()),
                                Types.VARCHAR));
        FilaFicha despues = fila(ejemplarId);
        auditoria.registrarCambio(
                ENTIDAD_EJEMPLAR,
                ejemplarId,
                "Modificacion",
                valores(antes),
                valores(despues),
                "Ficha técnica",
                usuario.id());
        return ficha(ejemplarId, usuario);
    }

    @Transactional
    public Evaluacion evaluar(long ejemplarId, NuevaEvaluacion entrada, Usuario usuario) {
        exigirRegistro(usuario);
        fila(ejemplarId);
        if (entrada.fecha().isAfter(LocalDate.now(reloj))) {
            throw new ErrorCoberturas(
                    400, "fecha-futura", "La fecha de la evaluación no puede ser futura");
        }
        List<String> hallazgos =
                Stream.of(
                                entrada.enfermedadesPlagas(),
                                entrada.danosMecanicosZunchado(),
                                entrada.inclinacionRamasSecas(),
                                entrada.cavidadesPudricion(),
                                entrada.raicesExpuestas(),
                                entrada.interferenciaInfraestructura())
                        .map(FichaTecnicaService::texto)
                        .toList();
        boolean conHallazgos = hallazgos.stream().anyMatch(h -> h != null);
        boolean sinHallazgos = Boolean.TRUE.equals(entrada.sinHallazgos());
        if (!sinHallazgos && !conHallazgos) {
            throw new ErrorCoberturas(
                    400,
                    "evaluacion-vacia",
                    "Registra al menos un hallazgo o marca la evaluación sin hallazgos");
        }
        if (sinHallazgos && conHallazgos) {
            throw new ErrorCoberturas(
                    400,
                    "evaluacion-contradictoria",
                    "Una evaluación sin hallazgos no puede tener hallazgos");
        }
        String manejo = texto(entrada.manejoRecomendado());
        if (conHallazgos && manejo == null) {
            throw new ErrorCoberturas(
                    400, "manejo-obligatorio", "Indica el manejo recomendado para los hallazgos");
        }
        long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod02_evaluacion_ejemplar (ejemplar_id, fecha, sin_hallazgos,
                            enfermedades_plagas, danos_mecanicos_zunchado,
                            inclinacion_ramas_secas, cavidades_pudricion, raices_expuestas,
                            interferencia_infraestructura, manejo_recomendado, usuario_id)
                        VALUES (:ejemplar, :fecha, :sin, :e1, :e2, :e3, :e4, :e5, :e6, :manejo,
                            :usuario)
                        RETURNING id
                        """,
                        new MapSqlParameterSource()
                                .addValue("ejemplar", ejemplarId)
                                .addValue("fecha", entrada.fecha())
                                .addValue("sin", sinHallazgos)
                                .addValue("e1", hallazgos.get(0), Types.VARCHAR)
                                .addValue("e2", hallazgos.get(1), Types.VARCHAR)
                                .addValue("e3", hallazgos.get(2), Types.VARCHAR)
                                .addValue("e4", hallazgos.get(3), Types.VARCHAR)
                                .addValue("e5", hallazgos.get(4), Types.VARCHAR)
                                .addValue("e6", hallazgos.get(5), Types.VARCHAR)
                                .addValue("manejo", manejo, Types.VARCHAR)
                                .addValue("usuario", usuario.id()),
                        Long.class);
        Evaluacion creada =
                ficha(ejemplarId, usuario).evaluaciones().stream()
                        .filter(e -> e.id() == id)
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("ejemplar_id", ejemplarId);
        valores.put("fecha", String.valueOf(creada.fecha()));
        valores.put("sin_hallazgos", creada.sinHallazgos());
        valores.put("manejo_recomendado", creada.manejoRecomendado());
        auditoria.registrarAlta(ENTIDAD_EVALUACION, id, valores, usuario.id());
        return creada;
    }

    private static void exigirRegistro(Usuario usuario) {
        AccesoCoberturas.consultar(usuario);
        AccesoCoberturas.exigir(
                usuario,
                AccesoCoberturas.REGISTRAR,
                "Tu rol no puede registrar la ficha técnica del ejemplar");
    }

    private FilaFicha fila(long ejemplarId) {
        return jdbc
                .query(
                        """
                        SELECT e.id, e.codigo, s.nombre_comun especie_nombre_comun,
                            s.nombre_cientifico especie_nombre_cientifico,
                            s.forma_biologica especie_forma_biologica,
                            a.nombre area_verde_nombre, c.nombre cuartel_nombre,
                            e.altura_total_m, e.altura_fuste_m, e.dap_cm, e.radio_copa_m,
                            e.origen, e.estado_salud
                        FROM mod02_ejemplar e
                        LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                        LEFT JOIN mod02_area_verde a ON a.id = e.area_verde_id
                        LEFT JOIN mod02_cuartel c ON c.id = a.cuartel_id
                        WHERE e.id = :id
                        """,
                        Map.of("id", ejemplarId),
                        new DataClassRowMapper<>(FilaFicha.class))
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new ErrorCoberturas(
                                        404, "ejemplar-no-encontrado", "El ejemplar no existe"));
    }

    private static BigDecimal valor(
            String campo, BigDecimal nuevo, BigDecimal anterior, List<String> vaciar) {
        if (vaciar.contains(campo)) {
            return null;
        }
        return nuevo != null ? nuevo : anterior;
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }

    private static Map<String, Object> valores(FilaFicha f) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("altura_total_m", f.alturaTotalM());
        valores.put("altura_fuste_m", f.alturaFusteM());
        valores.put("dap_cm", f.dapCm());
        valores.put("radio_copa_m", f.radioCopaM());
        valores.put("origen", nombre(f.origen()));
        valores.put("estado_salud", nombre(f.estadoSalud()));
        return valores;
    }
}
