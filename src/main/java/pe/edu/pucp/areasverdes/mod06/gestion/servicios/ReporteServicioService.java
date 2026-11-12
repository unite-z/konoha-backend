package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod05.Atencion;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ServiciosTercerizadosRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ServiciosTercerizadosRepository.AtencionServicio;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ServiciosTercerizadosRepository.Atendido;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Avance;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Conformidad;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.ContratoReportado;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.ElementoAtendido;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.EspeciesPlagas;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.FilaAtencion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Filtros;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Referencia;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Rendimiento;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ReporteServicioTercerizado.Servicio;

/**
 * RF-REP-008 · CU-MOD06-002: reporte por servicio tercerizado con avance, rendimiento, especies o
 * plagas atendidas y estado de conformidad. Solo lectura.
 *
 * <p>Reglas (las marcadas SUPUESTO están también en el frontend):
 *
 * <ul>
 *   <li>Ejecutada: estado «Ejecutado pendiente de reporte» o posterior; en ejecución: «En
 *       ejecución»; pendiente: «Requerimiento registrado» u «OC referenciada».
 *   <li>Avance = ejecutadas / (programadas + extraordinarias), con dos decimales.
 *   <li>Conformidad: la decisión de MOD-05 (V70) sobre la última revisión del informe. Si el
 *       proveedor subió una revisión posterior a la decisión, está pendiente de revisión. Una
 *       observación sin revisión nueva es una subsanación abierta y cuenta como «Observada»
 *       (SUPUESTO).
 *   <li>Metrado previsto: el del alcance de cada atención. Ejecutado: el que declara el proveedor
 *       en los datos de su informe final (AV-320) o, si no lo declara, el previsto de esa atención.
 *   <li>Rendimiento: metrado ejecutado por hora entre inicio y fin reales.
 *   <li>Fuera de plazo: MOD-05 no guarda un plazo por atención; lo tienen las que vienen de una
 *       programación de MOD-03, cuyo fin previsto es el plazo. Va fuera de plazo la que terminó
 *       después de ese día o, sin terminar, la que ya lo pasó. Si ninguna tiene plazo, {@code
 *       null}.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReporteServicioService {

    /** Filas del detalle; con más, el frontend avisa de que se muestran las primeras. */
    public static final int LIMITE_FILAS = 500;

    public static final String MENSAJE_SIN_DATOS =
            "No hay atenciones de este servicio en el periodo con los filtros elegidos";
    public static final String MENSAJE_SIN_FECHAS =
            "Ninguna atención ejecutada tiene fecha de inicio y de fin: no se puede calcular el"
                    + " rendimiento";
    public static final String MENSAJE_SIN_ESPECIES =
            "Las atenciones del periodo no registran plagas ni ejemplares con especie";
    public static final String MENSAJE_SIN_EJECUTADAS =
            "Aún no hay atenciones ejecutadas en el periodo: no hay informes que revisar";
    public static final String NOTA_LIMITE =
            "Se muestran las primeras " + LIMITE_FILAS + " atenciones del periodo";
    public static final String NOTA_UNIDADES =
            "Las atenciones usan unidades de metrado distintas: no se suman";
    public static final String NOTA_PLAZO =
            "Solo tienen plazo las atenciones que vienen de una programación: las demás no se"
                    + " cuentan como fuera de plazo";
    public static final String NOTA_METRADO_PREVISTO =
            "Algunos informes no declaran el metrado ejecutado: para esas atenciones se usa el"
                    + " previsto";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final Set<String> EJECUTADAS =
            Set.of(Atencion.EJECUTADO, Atencion.REPORTE, Atencion.OBSERVADO, Atencion.CONFORME);
    private static final Set<String> PENDIENTES = Set.of(Atencion.REQUERIMIENTO, Atencion.OC);

    private final ServiciosTercerizadosRepository repositorio;
    private final Clock reloj;

    public ReporteServicioService(ServiciosTercerizadosRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public ReporteServicioTercerizado generar(
            RangoFechas periodo, long tipoServicioId, Long proveedorId, Long contratoId) {
        String nombre =
                repositorio
                        .nombreTipoServicio(tipoServicioId)
                        .orElseThrow(
                                () ->
                                        ErrorGestion.validacion(
                                                "tipo_servicio_id",
                                                "No existe ese tipo de servicio"));
        Referencia proveedor =
                proveedorId == null
                        ? null
                        : repositorio
                                .razonSocial(proveedorId)
                                .map(r -> new Referencia(proveedorId, r))
                                .orElseThrow(
                                        () ->
                                                ErrorGestion.validacion(
                                                        "proveedor_id", "No existe ese proveedor"));
        ContratoReportado contrato =
                contratoId == null
                        ? null
                        : repositorio
                                .contrato(contratoId)
                                .map(
                                        c ->
                                                new ContratoReportado(
                                                        c.id(),
                                                        c.numero(),
                                                        c.vigenciaInicio(),
                                                        c.vigenciaFin()))
                                .orElseThrow(
                                        () ->
                                                ErrorGestion.validacion(
                                                        "contrato_id", "No existe ese contrato"));

        List<AtencionServicio> leidas =
                repositorio.atenciones(
                        periodo, tipoServicioId, proveedorId, contratoId, LIMITE_FILAS + 1);
        List<String> notas = new ArrayList<>();
        if (leidas.size() > LIMITE_FILAS) {
            notas.add(NOTA_LIMITE);
            leidas = leidas.subList(0, LIMITE_FILAS);
        }
        List<AtencionServicio> atenciones = leidas;
        Map<Long, List<Atendido>> atendidos = new LinkedHashMap<>();
        for (Atendido a :
                repositorio.atendidos(atenciones.stream().map(AtencionServicio::id).toList())) {
            atendidos.computeIfAbsent(a.atencionId(), k -> new ArrayList<>()).add(a);
        }

        boolean vacio = atenciones.isEmpty();
        List<AtencionServicio> ejecutadas =
                atenciones.stream().filter(a -> EJECUTADAS.contains(a.estado())).toList();
        List<FilaAtencion> filas =
                atenciones.stream()
                        .map(a -> fila(a, atendidos.getOrDefault(a.id(), List.of())))
                        .toList();
        Avance avance = avance(atenciones, ejecutadas, notas);
        LocalDate hoy = LocalDate.now(reloj.withZone(LIMA));
        Integer fueraDePlazo = fueraDePlazo(atenciones, hoy, notas);
        return new ReporteServicioTercerizado(
                periodo,
                new Filtros(tipoServicioId, proveedorId, contratoId),
                OffsetDateTime.now(reloj),
                vacio,
                vacio ? MENSAJE_SIN_DATOS : null,
                new Servicio(tipoServicioId, nombre, proveedor, contrato),
                avance,
                rendimiento(ejecutadas, avance, vacio, fueraDePlazo),
                especiesPlagas(atendidos, vacio),
                conformidad(ejecutadas, vacio),
                filas,
                (int) ejecutadas.stream().filter(a -> !a.conEvidencia()).count(),
                List.copyOf(new LinkedHashSet<>(notas)));
    }

    // ------------------------------------------------------------------ secciones

    private static Avance avance(
            List<AtencionServicio> atenciones,
            List<AtencionServicio> ejecutadas,
            List<String> notas) {
        if (atenciones.isEmpty()) {
            return new Avance(true, MENSAJE_SIN_DATOS, 0, 0, 0, 0, 0, null, null, null, null);
        }
        int extraordinarias =
                (int) atenciones.stream().filter(a -> "Extraordinaria".equals(a.clase())).count();
        // Solo cuentan las unidades de las atenciones con metrado: una sin metrado no es otra
        // unidad.
        Set<String> unidades = new LinkedHashSet<>();
        atenciones.stream()
                .filter(a -> a.metrado() != null && a.unidad() != null)
                .forEach(a -> unidades.add(a.unidad()));
        boolean unaUnidad = unidades.size() == 1;
        if (unidades.size() > 1) notas.add(NOTA_UNIDADES);
        if (unaUnidad
                && ejecutadas.stream()
                        .anyMatch(a -> a.metrado() != null && a.metradoReportado() == null)) {
            notas.add(NOTA_METRADO_PREVISTO);
        }
        return new Avance(
                false,
                null,
                atenciones.size() - extraordinarias,
                extraordinarias,
                ejecutadas.size(),
                (int)
                        atenciones.stream()
                                .filter(a -> Atencion.EJECUCION.equals(a.estado()))
                                .count(),
                (int) atenciones.stream().filter(a -> PENDIENTES.contains(a.estado())).count(),
                porcentaje(ejecutadas.size(), atenciones.size()),
                unaUnidad ? texto(suma(atenciones, AtencionServicio::metrado)) : null,
                unaUnidad
                        ? texto(suma(ejecutadas, ReporteServicioService::metradoEjecutado))
                        : null,
                unaUnidad ? unidades.iterator().next() : null);
    }

    private static Rendimiento rendimiento(
            List<AtencionServicio> ejecutadas, Avance avance, boolean vacio, Integer fueraDePlazo) {
        if (vacio) return new Rendimiento(true, MENSAJE_SIN_DATOS, null, null, null, null, null);
        List<Duration> duraciones = new ArrayList<>();
        BigDecimal metrado = BigDecimal.ZERO;
        for (AtencionServicio a : ejecutadas) {
            if (a.fechaHoraInicio() == null || a.fechaHoraFin() == null) continue;
            duraciones.add(Duration.between(a.fechaHoraInicio(), a.fechaHoraFin()));
            if (metradoEjecutado(a) != null) metrado = metrado.add(metradoEjecutado(a));
        }
        if (duraciones.isEmpty()) {
            return new Rendimiento(true, MENSAJE_SIN_FECHAS, null, null, null, null, fueraDePlazo);
        }
        BigDecimal horas =
                BigDecimal.valueOf(duraciones.stream().mapToLong(Duration::toMinutes).sum())
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        String unidad = avance.unidadMetrado();
        String porHora =
                unidad == null || horas.signum() == 0
                        ? null
                        : texto(metrado.divide(horas, 2, RoundingMode.HALF_UP));
        return new Rendimiento(
                false,
                null,
                texto(horas),
                porHora,
                porHora == null ? null : unidad + "/h",
                texto(horas.divide(BigDecimal.valueOf(duraciones.size()), 2, RoundingMode.HALF_UP)),
                fueraDePlazo);
    }

    /**
     * Atenciones fuera de plazo entre las que tienen plazo (fin previsto de su programación). Una
     * terminada cuenta si terminó después de ese día; una sin terminar, si hoy ya lo pasó. Sin
     * ninguna con plazo, {@code null}: no se puede decir que haya cero.
     */
    static Integer fueraDePlazo(
            List<AtencionServicio> atenciones, LocalDate hoy, List<String> notas) {
        List<AtencionServicio> conPlazo =
                atenciones.stream().filter(a -> a.finPrevisto() != null).toList();
        if (conPlazo.size() < atenciones.size()) notas.add(NOTA_PLAZO);
        if (conPlazo.isEmpty()) return null;
        return (int)
                conPlazo.stream()
                        .filter(
                                a -> {
                                    LocalDate fin =
                                            a.fechaHoraFin() == null
                                                    ? null
                                                    : a.fechaHoraFin()
                                                            .atZoneSameInstant(LIMA)
                                                            .toLocalDate();
                                    return fin != null
                                            ? fin.isAfter(a.finPrevisto())
                                            : hoy.isAfter(a.finPrevisto());
                                })
                        .count();
    }

    /** Lo que declara el proveedor en su informe o, si no lo declara, lo previsto. */
    private static BigDecimal metradoEjecutado(AtencionServicio a) {
        return a.metradoReportado() != null ? a.metradoReportado() : a.metrado();
    }

    private static EspeciesPlagas especiesPlagas(
            Map<Long, List<Atendido>> atendidos, boolean vacio) {
        if (vacio) return new EspeciesPlagas(true, MENSAJE_SIN_DATOS, List.of());
        Map<List<String>, Set<Long>> atenciones =
                new TreeMap<>(Comparator.comparing(Object::toString));
        Map<List<String>, Set<Long>> ejemplares =
                new TreeMap<>(Comparator.comparing(Object::toString));
        atendidos.values().stream()
                .flatMap(List::stream)
                .forEach(
                        a -> {
                            List<String> clave = List.of(a.tipo(), a.nombre());
                            atenciones
                                    .computeIfAbsent(clave, k -> new LinkedHashSet<>())
                                    .add(a.atencionId());
                            if (a.ejemplarId() != null) {
                                ejemplares
                                        .computeIfAbsent(clave, k -> new LinkedHashSet<>())
                                        .add(a.ejemplarId());
                            }
                        });
        if (atenciones.isEmpty()) return new EspeciesPlagas(true, MENSAJE_SIN_ESPECIES, List.of());
        List<ElementoAtendido> elementos = new ArrayList<>();
        atenciones.forEach(
                (clave, ids) ->
                        elementos.add(
                                new ElementoAtendido(
                                        clave.get(0),
                                        clave.get(1),
                                        ids.size(),
                                        clave.get(0).equals("Especie")
                                                ? ejemplares.getOrDefault(clave, Set.of()).size()
                                                : null)));
        elementos.sort(
                Comparator.comparing(ElementoAtendido::atenciones)
                        .reversed()
                        .thenComparing(ElementoAtendido::nombre));
        return new EspeciesPlagas(false, null, elementos);
    }

    private static Conformidad conformidad(List<AtencionServicio> ejecutadas, boolean vacio) {
        if (vacio) return new Conformidad(true, MENSAJE_SIN_DATOS, 0, 0, 0, 0, 0);
        if (ejecutadas.isEmpty())
            return new Conformidad(true, MENSAJE_SIN_EJECUTADAS, 0, 0, 0, 0, 0);
        int conformes = 0, observadas = 0, pendientes = 0, sinReporte = 0, abiertas = 0;
        for (AtencionServicio a : ejecutadas) {
            String resultado = resultadoVigente(a);
            if (a.ultimaRevision() == null) sinReporte++;
            else if (resultado == null) pendientes++;
            else if (resultado.equals("Conforme")) conformes++;
            else {
                observadas++;
                abiertas++;
            }
        }
        return new Conformidad(
                false, null, conformes, observadas, pendientes, sinReporte, abiertas);
    }

    // ------------------------------------------------------------------ utilidades

    /** La decisión de MOD-05 si es sobre la última revisión del informe; si no, {@code null}. */
    private static String resultadoVigente(AtencionServicio a) {
        if (a.revisionDecidida() == null
                || !Objects.equals(a.revisionDecidida(), a.ultimaRevision())) {
            return null;
        }
        return a.resultadoDecidido();
    }

    private static FilaAtencion fila(AtencionServicio a, List<Atendido> atendidos) {
        BigDecimal metrado = EJECUTADAS.contains(a.estado()) ? metradoEjecutado(a) : a.metrado();
        return new FilaAtencion(
                a.id(),
                a.codigo(),
                "Extraordinaria".equals(a.clase()) ? "Extraordinaria" : "Programada",
                a.estado(),
                a.estado(),
                a.espacio(),
                a.ejemplarCodigo(),
                a.fechaHoraInicio(),
                a.fechaHoraFin(),
                metrado == null ? null : texto(metrado),
                a.unidad(),
                resultadoVigente(a),
                a.documentos(),
                atendidos.stream().map(Atendido::nombre).distinct().sorted().toList(),
                a.conEvidencia(),
                a.esRiesgo());
    }

    private static BigDecimal suma(
            List<AtencionServicio> atenciones,
            java.util.function.Function<AtencionServicio, BigDecimal> metrado) {
        return atenciones.stream()
                .map(metrado)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@code parte / total} en porcentaje con dos decimales, o {@code null} si el total es 0. */
    static String porcentaje(int parte, int total) {
        if (total == 0) return null;
        return BigDecimal.valueOf(parte * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String texto(BigDecimal valor) {
        return valor.stripTrailingZeros().scale() < 0
                ? valor.setScale(0).toPlainString()
                : valor.stripTrailingZeros().toPlainString();
    }
}
