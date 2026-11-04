package pe.edu.pucp.areasverdes.mod01.importacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;

/**
 * Partes de mantenimiento: cada fila es una actividad de {@code mod04_actividad}, dentro del parte
 * diario de su cuadrilla y fecha, que se crea si falta. Una actividad se reconoce por parte, tipo,
 * área y hora, así que repetir la carga no la duplica. Los participantes no se importan: son datos
 * personales y se registran en MOD-04. Es una carga de oficina: exige verificar partes.
 */
@Component
class ImportadorPartes implements Importador {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Día 0 de las fechas numéricas de Excel. */
    private static final LocalDate ORIGEN_EXCEL = LocalDate.of(1899, 12, 30);

    private static final String CUADRILLA = "cuadrilla";
    private static final String FECHA = "fecha";
    private static final String HORA = "hora";
    private static final String TIPO = "tipo de actividad";
    private static final String AREA = "área verde";
    private static final String OBSERVACIONES = "observaciones";

    private record Referencias(long cuadrilla, long tipo, long area, OffsetDateTime momento) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;
    private final Clock reloj;

    ImportadorPartes(NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria, Clock reloj) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public String tipo() {
        return "partes";
    }

    @Override
    public String permiso() {
        return "CAM.PARTE.VERIFICAR";
    }

    @Override
    public List<String> columnas() {
        return List.of(CUADRILLA, FECHA, HORA, TIPO, AREA);
    }

    @Override
    public Clasificacion clasificar(Map<String, String> fila, Set<String> vistos) {
        String codigo =
                fila.getOrDefault(CUADRILLA, "")
                        + " · "
                        + fila.getOrDefault(FECHA, "")
                        + " "
                        + fila.getOrDefault(HORA, "");
        LocalDate fecha = fecha(fila.getOrDefault(FECHA, ""));
        LocalTime hora = hora(fila.getOrDefault(HORA, ""));
        if (fecha == null || hora == null) {
            return Clasificacion.error(codigo, "Fecha u hora no válidas");
        }
        if (fecha.isAfter(LocalDate.now(reloj.withZone(LIMA)))) {
            return Clasificacion.error(codigo, "La fecha no puede ser futura");
        }
        if (fila.getOrDefault(OBSERVACIONES, "").length() > 1000) {
            return Clasificacion.error(codigo, "Las observaciones superan los 1000 caracteres");
        }
        Long cuadrilla =
                id(
                        "SELECT id FROM mod04_cuadrilla WHERE activa AND lower(trim(nombre)) ="
                                + " lower(:n)",
                        fila.getOrDefault(CUADRILLA, ""));
        if (cuadrilla == null) {
            return Clasificacion.error(codigo, "Cuadrilla no registrada");
        }
        Long tipo =
                id(
                        "SELECT id FROM mod04_tipo_actividad WHERE activo AND lower(nombre) ="
                                + " lower(:n)",
                        fila.getOrDefault(TIPO, ""));
        if (tipo == null) {
            return Clasificacion.error(codigo, "Tipo de actividad no registrado");
        }
        Long area =
                id(
                        "SELECT id FROM mod05_areaverde WHERE activo AND lower(nombre) = lower(:n)",
                        fila.getOrDefault(AREA, ""));
        if (area == null) {
            return Clasificacion.error(codigo, "Área verde no registrada");
        }
        String clave = (cuadrilla + "|" + tipo + "|" + area + "|" + fecha + "T" + hora);
        if (!vistos.add(clave.toLowerCase(Locale.ROOT))) {
            return new Clasificacion(codigo, REVISAR, "Actividad repetida en el archivo", null);
        }
        Map<String, Object> p = new HashMap<>();
        p.put("cuadrilla", cuadrilla);
        p.put("fecha", fecha);
        String estado =
                jdbc
                        .queryForList(
                                "SELECT estado FROM mod04_parte_diario"
                                        + " WHERE cuadrilla_id = :cuadrilla AND fecha = :fecha",
                                p,
                                String.class)
                        .stream()
                        .findFirst()
                        .orElse(null);
        OffsetDateTime momento = fecha.atTime(hora).atZone(LIMA).toOffsetDateTime();
        p.put("tipo", tipo);
        p.put("area", area);
        p.put("momento", momento);
        boolean existe =
                Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
                                SELECT EXISTS (SELECT 1 FROM mod04_actividad a
                                    JOIN mod04_parte_diario d ON d.id = a.parte_id
                                    WHERE d.cuadrilla_id = :cuadrilla AND d.fecha = :fecha
                                    AND a.tipo_actividad_id = :tipo AND a.area_verde_id = :area
                                    AND a.fecha_hora_actividad = :momento)
                                """,
                                p,
                                Boolean.class));
        if (existe) {
            return new Clasificacion(codigo, SIN_CAMBIOS, "", null);
        }
        if ("Cerrado".equals(estado)) {
            return Clasificacion.error(codigo, "El parte de ese día ya está cerrado");
        }
        return new Clasificacion(codigo, ALTA, "", null);
    }

    @Override
    public void aplicar(Map<String, String> fila, Clasificacion clasificacion, long usuarioId) {
        LocalDate fecha = fecha(fila.get(FECHA));
        Referencias r =
                new Referencias(
                        id(
                                "SELECT id FROM mod04_cuadrilla WHERE activa AND"
                                        + " lower(trim(nombre)) = lower(:n)",
                                fila.get(CUADRILLA)),
                        id(
                                "SELECT id FROM mod04_tipo_actividad WHERE activo AND lower(nombre)"
                                        + " = lower(:n)",
                                fila.get(TIPO)),
                        id(
                                "SELECT id FROM mod05_areaverde WHERE activo AND lower(nombre) ="
                                        + " lower(:n)",
                                fila.get(AREA)),
                        fecha.atTime(hora(fila.get(HORA))).atZone(LIMA).toOffsetDateTime());
        Map<String, Object> p = new HashMap<>();
        p.put("cuadrilla", r.cuadrilla());
        p.put("fecha", fecha);
        p.put("usuario", usuarioId);
        Long parte =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod04_parte_diario (cuadrilla_id, capataz_id, fecha,
                            usuario_creacion_id)
                        SELECT c.id, c.capataz_id, :fecha, :usuario FROM mod04_cuadrilla c
                        WHERE c.id = :cuadrilla
                        ON CONFLICT (cuadrilla_id, fecha) DO UPDATE SET estado = mod04_parte_diario.estado
                        RETURNING id
                        """,
                        p,
                        Long.class);
        p.put("parte", parte);
        p.put("tipo", r.tipo());
        p.put("area", r.area());
        p.put("momento", r.momento());
        String observaciones = fila.getOrDefault(OBSERVACIONES, "");
        p.put("observaciones", observaciones.isEmpty() ? null : observaciones);
        Long id =
                jdbc.queryForObject(
                        """
                        INSERT INTO mod04_actividad (parte_id, tipo_actividad_id, area_verde_id,
                            fecha_hora_actividad, observaciones, usuario_creacion_id)
                        VALUES (:parte, :tipo, :area, :momento, :observaciones, :usuario)
                        RETURNING id
                        """,
                        p,
                        Long.class);
        auditoria.registrarAlta(
                "actividad",
                id,
                Map.of("parte_id", parte, "tipo_actividad_id", r.tipo(), "area_verde_id", r.area()),
                usuarioId);
    }

    /** {@code consulta} solo recibe las constantes de esta clase. */
    private Long id(String consulta, String nombre) {
        return jdbc.queryForList(consulta, Map.of("n", nombre), Long.class).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Admite {@code AAAA-MM-DD} y las fechas numéricas con que Excel guarda las celdas de fecha.
     */
    private static LocalDate fecha(String valor) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            try {
                return ORIGEN_EXCEL.plusDays(new BigDecimal(valor).longValue());
            } catch (NumberFormatException | DateTimeException | ArithmeticException otra) {
                return null;
            }
        }
    }

    /** Admite {@code HH:MM} y las horas numéricas de Excel, que son una fracción del día. */
    private static LocalTime hora(String valor) {
        try {
            return LocalTime.parse(valor);
        } catch (DateTimeParseException e) {
            try {
                double fraccion = Double.parseDouble(valor);
                return fraccion >= 0 && fraccion < 1
                        ? LocalTime.ofSecondOfDay(Math.round(fraccion * 86_400) % 86_400)
                        : null;
            } catch (NumberFormatException otra) {
                return null;
            }
        }
    }
}
