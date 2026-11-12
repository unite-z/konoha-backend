package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.gestion.RangoFechas;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProgramadoEjecutadoRepository;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProgramadoEjecutadoRepository.AtencionSuelta;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProgramadoEjecutadoRepository.ProgramacionRealizada;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado.Ejecucion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado.Fila;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado.Filtros;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado.PorServicio;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.ProgramadoEjecutado.Resumen;
import pe.edu.pucp.areasverdes.mod06.servicios.AtencionesEspacioSectorService;

/**
 * RF-REP-010: compara las programaciones de MOD-03 de un periodo con lo ejecutado. Solo lectura.
 *
 * <p>Ejecución de una programación: su atención tercerizada de MOD-05 ({@code atencion_id}) o, si
 * no tiene, las fechas reales que registró MOD-03 en la propia programación (origen {@code
 * Actividad}). Situación, como {@code situacionDe} del frontend (SUPUESTO: tolerancia de 0 días):
 *
 * <ul>
 *   <li>Cancelado: programación cancelada.
 *   <li>Ejecutado / EjecutadoConDesviacion: hay fin real; desviación = fin real − fin previsto.
 *   <li>EnCurso: hay inicio real sin fin.
 *   <li>Pendiente: sin ejecución o sin inicio real.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProgramadoEjecutadoService {

    public static final String PERSONAL_PROPIO = AtencionesEspacioSectorService.PERSONAL_PROPIO;
    public static final String TERCERIZADO = AtencionesEspacioSectorService.TERCERIZADO;
    public static final List<String> SITUACIONES =
            List.of("Ejecutado", "EjecutadoConDesviacion", "EnCurso", "Pendiente", "Cancelado");
    private static final Map<String, String> ETIQUETAS =
            Map.of(
                    "Ejecutado", "Ejecutado",
                    "EjecutadoConDesviacion", "Con desviación",
                    "EnCurso", "En curso",
                    "Pendiente", "Pendiente",
                    "Cancelado", "Cancelado");

    public static final String MENSAJE_SIN_DATOS =
            "No hay trabajos programados ni ejecutados en el periodo con los filtros elegidos";
    public static final String MENSAJE_PERSONAL_PROPIO =
            "Las actividades del personal propio aún no se vinculan con una programación en"
                    + " Operación de campo: solo se comparan las fechas reales de la programación";
    public static final String NOTA_TOLERANCIA =
            "Cualquier día de retraso del fin real frente al fin previsto cuenta como desviación";

    private final ProgramadoEjecutadoRepository repositorio;
    private final Clock reloj;

    public ProgramadoEjecutadoService(ProgramadoEjecutadoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public ProgramadoEjecutado comparar(
            RangoFechas periodo,
            Long tipoServicioId,
            Long areaVerdeId,
            String modalidad,
            String situacion) {
        List<Fila> filas = new ArrayList<>();
        Map<String, Long> idsServicio = new LinkedHashMap<>();
        for (ProgramacionRealizada p :
                repositorio.programaciones(periodo, tipoServicioId, areaVerdeId)) {
            idsServicio.putIfAbsent(p.tipoServicio(), p.tipoServicioId());
            Fila fila = fila(p);
            if (modalidad != null && !modalidad.equals(fila.modalidad())) continue;
            if (situacion != null && !situacion.equals(fila.situacion())) continue;
            filas.add(fila);
        }
        List<Ejecucion> sueltas =
                PERSONAL_PROPIO.equals(modalidad) || situacion != null
                        ? List.of()
                        : repositorio
                                .atencionesSinProgramacion(periodo, tipoServicioId, areaVerdeId)
                                .stream()
                                .map(ProgramadoEjecutadoService::atencion)
                                .toList();
        boolean vacio = filas.isEmpty() && sueltas.isEmpty();
        return new ProgramadoEjecutado(
                periodo,
                new Filtros(tipoServicioId, areaVerdeId, modalidad, situacion),
                OffsetDateTime.now(reloj),
                vacio,
                vacio ? MENSAJE_SIN_DATOS : null,
                resumen(filas, sueltas.size()),
                porServicio(filas, idsServicio),
                filas,
                sueltas,
                new SeccionNoDisponible(true, MENSAJE_PERSONAL_PROPIO),
                List.of(NOTA_TOLERANCIA));
    }

    private static Fila fila(ProgramacionRealizada p) {
        String modalidad =
                p.proveedorId() != null || TERCERIZADO.equals(p.ejecutor())
                        ? TERCERIZADO
                        : PERSONAL_PROPIO;
        List<Ejecucion> ejecuciones = new ArrayList<>();
        LocalDate inicioReal;
        LocalDate finReal;
        if (p.atencionId() != null) {
            ejecuciones.add(
                    new Ejecucion(
                            "Atencion",
                            p.atencionId(),
                            p.atencionCodigo(),
                            p.atencionEstado(),
                            p.atencionEstado(),
                            p.atencionInicio(),
                            p.atencionFin()));
            inicioReal = fechaLocal(p.atencionInicio());
            finReal = fechaLocal(p.atencionFin());
        } else {
            inicioReal = p.fechaInicioReal();
            finReal = p.fechaFinReal();
            if (inicioReal != null) {
                ejecuciones.add(
                        new Ejecucion(
                                "Actividad",
                                p.id(),
                                p.codigo() != null ? p.codigo() : "Programación " + p.id(),
                                p.estado(),
                                p.estado(),
                                inicioDelDia(inicioReal),
                                finReal == null ? null : inicioDelDia(finReal)));
            }
        }
        String situacion;
        Integer desviacion = null;
        if ("Cancelada".equals(p.estado())) {
            situacion = "Cancelado";
        } else if (finReal != null) {
            desviacion = (int) ChronoUnit.DAYS.between(p.fechaFinPrevista(), finReal);
            situacion = desviacion > 0 ? "EjecutadoConDesviacion" : "Ejecutado";
        } else if (inicioReal != null) {
            situacion = "EnCurso";
        } else {
            situacion = "Pendiente";
        }
        return new Fila(
                p.id(),
                p.tipoServicio(),
                modalidad,
                p.proveedor() != null ? p.proveedor() : p.cuadrilla(),
                p.espacio(),
                p.ventanaMes(),
                p.fechaInicioPrevista(),
                p.fechaFinPrevista(),
                p.estado(),
                ejecuciones,
                situacion,
                ETIQUETAS.get(situacion),
                desviacion);
    }

    private static Ejecucion atencion(AtencionSuelta a) {
        return new Ejecucion(
                "Atencion", a.id(), a.codigo(), a.estado(), a.estado(), a.inicio(), a.fin());
    }

    private static Resumen resumen(List<Fila> filas, int sinProgramacion) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        SITUACIONES.forEach(s -> cuenta.put(s, 0));
        filas.forEach(f -> cuenta.merge(f.situacion(), 1, Integer::sum));
        int cumplidos = cuenta.get("Ejecutado") + cuenta.get("EjecutadoConDesviacion");
        int base = filas.size() - cuenta.get("Cancelado");
        return new Resumen(
                filas.size(),
                cuenta.get("Ejecutado"),
                cuenta.get("EjecutadoConDesviacion"),
                cuenta.get("EnCurso"),
                cuenta.get("Pendiente"),
                cuenta.get("Cancelado"),
                base == 0
                        ? null
                        : BigDecimal.valueOf(cumplidos * 100L)
                                .divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP)
                                .toPlainString(),
                sinProgramacion);
    }

    private static List<PorServicio> porServicio(List<Fila> filas, Map<String, Long> ids) {
        Map<String, int[]> totales = new LinkedHashMap<>();
        Map<String, Fila> ejemplo = new LinkedHashMap<>();
        for (Fila f : filas) {
            String clave = f.tipoServicio() + "|" + f.modalidad();
            int[] t = totales.computeIfAbsent(clave, k -> new int[3]);
            ejemplo.putIfAbsent(clave, f);
            t[0]++;
            if (f.situacion().startsWith("Ejecutado")) t[1]++;
            if (f.situacion().equals("Pendiente")) t[2]++;
        }
        return totales.entrySet().stream()
                .map(
                        e -> {
                            Fila f = ejemplo.get(e.getKey());
                            return new PorServicio(
                                    ids.getOrDefault(f.tipoServicio(), 0L),
                                    f.tipoServicio(),
                                    f.modalidad(),
                                    e.getValue()[0],
                                    e.getValue()[1],
                                    e.getValue()[2]);
                        })
                .toList();
    }

    private static LocalDate fechaLocal(OffsetDateTime instante) {
        return instante == null ? null : instante.atZoneSameInstant(Fechas.LIMA).toLocalDate();
    }

    private static OffsetDateTime inicioDelDia(LocalDate dia) {
        return dia.atStartOfDay(Fechas.LIMA).toOffsetDateTime();
    }
}
