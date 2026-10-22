package pe.edu.pucp.areasverdes.mod06.servicios;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Periodo;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.SelectorFuenteDatos;

/**
 * Servicio de RF-REP-002: cumplimiento del corte de césped.
 *
 * <p>Trazabilidad: RF-REP-002 · HU-MOD06-009 · CU-MOD06-009 · CP-TR-MOD06-009 · RN-001, RN-002, RI-24 ·
 * CU-MOD03-001, 002, 010 (1a), 012 y 014.
 *
 * <p>Texto del requisito (Catálogo v2): «El sistema deberá mostrar el cumplimiento del corte de césped: días
 * transcurridos entre ciclos respecto a la frecuencia y días de ejecución respecto a la duración máxima».
 *
 * <p>Reglas de cálculo [Definición acordada 20/10]:
 * <ol>
 *   <li>Ciclos evaluables: los que tienen {@code fecha_inicio_real}. Un ciclo «Programado» sin fecha real no se
 *   evalúa (CU-MOD03-012 2a).</li>
 *   <li>Ciclos del periodo: evaluables con {@code fecha_inicio_real} dentro del periodo, por fecha ascendente.</li>
 *   <li>Ciclo anterior: el evaluable inmediatamente anterior en <b>todo el historial</b> (aunque quede fuera del
 *   periodo).</li>
 *   <li>Días entre ciclos = fecha_inicio_real(n) − fecha_inicio_real(n−1), en días naturales.</li>
 *   <li>Estación aplicable: la de la ProgramacionServicio del ciclo n; límites en FrecuenciaServicio
 *   (tipo_servicio_id + estacion).</li>
 *   <li>Estado de frecuencia: «Límite no configurado» / «Dentro de la frecuencia» (≤ práctica) / «Tolerado»
 *   (≤ máxima) / «Fuera de frecuencia» (CU-MOD03-010 1a). Con un solo límite: dentro o fuera respecto de ese
 *   límite.</li>
 *   <li>Días de corte = fecha_fin_corte − fecha_inicio_real + 1 (nulo si el ciclo sigue en curso).</li>
 *   <li>Días de limpieza = fecha_fin_limpieza − fecha_inicio_limpieza + 1 (nulo si falta alguna).</li>
 *   <li>Estado de duración frente a DuracionServicio (RN-002: 3 días de corte más 1 de limpieza).</li>
 *   <li>RI-24: el resultado es informativo («alerta, no bloqueo»). El servicio no modifica nada.</li>
 * </ol>
 *
 * <p>HU-MOD06-009: si faltan fechas o límites, se informa de la falta ({@code faltantes}) y no se calcula el
 * cumplimiento.
 */
@Service
public class CumplimientoCorteService {

    public static final String CODIGO_INDICADOR = "RF-REP-002";
    public static final String MENSAJE_SIN_DATOS = "No hay ciclos de corte con fecha de inicio en el periodo elegido";

    // Estados de frecuencia
    public static final String SIN_CICLO_ANTERIOR = "Sin ciclo anterior";
    public static final String LIMITE_NO_CONFIGURADO = "Límite no configurado";
    public static final String DENTRO_FRECUENCIA = "Dentro de la frecuencia";
    public static final String TOLERADO = "Tolerado";
    public static final String FUERA_FRECUENCIA = "Fuera de frecuencia";
    public static final List<String> ESTADOS_FRECUENCIA =
        List.of(SIN_CICLO_ANTERIOR, LIMITE_NO_CONFIGURADO, DENTRO_FRECUENCIA, TOLERADO, FUERA_FRECUENCIA);

    // Estados de duración
    public static final String SIN_FECHA_FIN_CORTE = "Sin fecha de fin de corte";
    public static final String DENTRO_MAXIMO = "Dentro del máximo";
    public static final String EXCEDE_MAXIMO = "Excede el máximo";
    public static final List<String> ESTADOS_DURACION =
        List.of(LIMITE_NO_CONFIGURADO, SIN_FECHA_FIN_CORTE, DENTRO_MAXIMO, EXCEDE_MAXIMO);

    private final SelectorFuenteDatos selector;
    private final Clock reloj;

    public CumplimientoCorteService(SelectorFuenteDatos selector, Clock reloj) {
        this.selector = selector;
        this.reloj = reloj;
    }

    /**
     * Calcula el cumplimiento del corte de césped en {@code periodo} (DefinicionIndicador.calcular, Diagrama de
     * Clases v1).
     *
     * <p>RF-REP-002 · HU-MOD06-009 · CP-TR-MOD06-009. Solo lectura (CU-MOD06-001).
     * Este reporte no admite filtros adicionales; {@code filtros} existe por uniformidad con la operación
     * {@code calcular}.
     */
    public Map<String, Object> calcular(Periodo periodo, Map<String, Object> filtros) {
        return calcular(periodo, null, filtros);
    }

    public Map<String, Object> calcular(Periodo periodo, FuenteDatos fuente, Map<String, Object> filtros) {
        fuente = fuente != null ? fuente : selector.obtenerFuenteDatos();
        Map<String, Object> indicador = Comunes.indicadorPorCodigo(fuente, CODIGO_INDICADOR);
        Map<String, Object> filtrosAplicados = Comunes.filtrosAplicados(filtros);

        List<Map<String, Object>> evaluables = new ArrayList<>();
        for (Map<String, Object> c : fuente.listar("CicloCorte")) {
            if (Fechas.aFecha(c.get("fecha_inicio_real")) != null) evaluables.add(c);
        }
        evaluables.sort((a, b) -> Py.compare(
            java.util.Arrays.asList(Fechas.aFecha(Py.item(a, "fecha_inicio_real")), Py.item(a, "id")),
            java.util.Arrays.asList(Fechas.aFecha(Py.item(b, "fecha_inicio_real")), Py.item(b, "id"))));
        List<Map<String, Object>> delPeriodo = new ArrayList<>();
        for (Map<String, Object> c : evaluables) {
            if (periodo.contiene(Fechas.aFecha(Py.item(c, "fecha_inicio_real")))) delPeriodo.add(c);
        }

        if (delPeriodo.isEmpty()) {
            return resultado(periodo, filtrosAplicados, true, MENSAJE_SIN_DATOS, indicador, List.of(), null, null);
        }

        Contexto contexto = new Contexto(fuente);
        Map<Object, Integer> posicion = new LinkedHashMap<>();
        for (int i = 0; i < evaluables.size(); i++) posicion.put(Py.k(Py.item(evaluables.get(i), "id")), i);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Map<String, Object> ciclo : delPeriodo) {
            int p = posicion.get(Py.k(Py.item(ciclo, "id")));
            filas.add(evaluarCiclo(ciclo, p > 0 ? evaluables.get(p - 1) : null, contexto));
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total_ciclos", (long) filas.size());
        resumen.put("por_estado_frecuencia", contar(filas, "estado_frecuencia", ESTADOS_FRECUENCIA));
        resumen.put("por_estado_duracion", contar(filas, "estado_duracion", ESTADOS_DURACION));
        return resultado(periodo, filtrosAplicados, false, null, indicador, filas, resumen, filas.get(filas.size() - 1));
    }

    private Map<String, Object> resultado(Periodo periodo, Map<String, Object> filtrosAplicados, boolean sinDatos,
                                          String mensaje, Map<String, Object> indicador, List<Map<String, Object>> ciclos,
                                          Map<String, Object> resumen, Map<String, Object> ultimoCiclo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periodo", periodo.comoDict());
        r.put("filtros_aplicados", filtrosAplicados);
        r.put("generado_en", Fechas.ahoraIso(reloj));
        r.put("sin_datos", sinDatos);
        r.put("mensaje_sin_datos", mensaje);
        r.put("indicador", indicador);
        r.put("ciclos", ciclos);
        r.put("resumen", resumen);
        r.put("ultimo_ciclo", ultimoCiclo);
        return r;
    }

    /** Catálogos y registros relacionados, leídos una vez por cálculo. */
    private static final class Contexto {
        final Map<Object, Map<String, Object>> programaciones;
        final List<Map<String, Object>> frecuencias;
        final List<Map<String, Object>> duraciones;
        final Map<Object, Map<String, Object>> proveedores;
        final Map<Object, Map<String, Object>> rutas;

        Contexto(FuenteDatos fuente) {
            programaciones = Py.indexar(fuente.listar("ProgramacionServicio"), "id");
            frecuencias = fuente.listar("FrecuenciaServicio");
            duraciones = fuente.listar("DuracionServicio");
            proveedores = Py.indexar(fuente.listar("Proveedor"), "id");
            rutas = Py.indexar(fuente.listar("RutaCorte"), "id");
        }

        Map<String, Object> frecuencia(Object tipoServicioId, Object estacion) {
            for (Map<String, Object> f : frecuencias) {
                if (Py.eq(f.get("tipo_servicio_id"), tipoServicioId) && Py.eq(f.get("estacion"), estacion)) return f;
            }
            return null;
        }

        Map<String, Object> duracion(Object tipoServicioId) {
            for (Map<String, Object> d : duraciones) {
                if (Py.eq(d.get("tipo_servicio_id"), tipoServicioId)) return d;
            }
            return null;
        }
    }

    private static Object diasInclusivos(LocalDate inicio, LocalDate fin) {
        if (inicio == null || fin == null) return null;
        return ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    /** Regla 6 (sin el caso de límite faltante, que se resuelve antes). */
    private static String estadoFrecuencia(Object dias, Object practica, Object maxima) {
        if (practica != null && maxima != null) {
            if (Py.compare(dias, practica) <= 0) return DENTRO_FRECUENCIA;
            if (Py.compare(dias, maxima) <= 0) return TOLERADO;
            return FUERA_FRECUENCIA;
        }
        Object limite = practica != null ? practica : maxima;
        return Py.compare(dias, limite) <= 0 ? DENTRO_FRECUENCIA : FUERA_FRECUENCIA;
    }

    private static Map<String, Object> evaluarCiclo(Map<String, Object> ciclo, Map<String, Object> anterior, Contexto ctx) {
        List<String> faltantes = new ArrayList<>();

        Map<String, Object> programacion = ctx.programaciones.get(Py.k(ciclo.get("programacion_id")));
        Object tipoServicioId = Py.truthy(programacion) ? programacion.get("tipo_servicio_id") : null;
        Object estacion = Py.truthy(programacion) ? programacion.get("estacion") : null;
        if (programacion == null) faltantes.add("El ciclo no tiene programación de servicio asociada");

        LocalDate inicio = Fechas.aFecha(Py.item(ciclo, "fecha_inicio_real"));
        LocalDate inicioAnterior = Py.truthy(anterior) ? Fechas.aFecha(Py.item(anterior, "fecha_inicio_real")) : null;
        Object diasEntre = inicioAnterior != null ? (Object) ChronoUnit.DAYS.between(inicioAnterior, inicio) : null;

        // --- Frecuencia (reglas 4 a 6) --------------------------------------
        Map<String, Object> frecuencia = Py.truthy(programacion) ? ctx.frecuencia(tipoServicioId, estacion) : null;
        Object teorica = Py.truthy(frecuencia) ? frecuencia.get("frecuencia_teorica_dias") : null;
        Object practica = Py.truthy(frecuencia) ? frecuencia.get("frecuencia_practica_dias") : null;
        Object maxima = Py.truthy(frecuencia) ? frecuencia.get("frecuencia_maxima_dias") : null;
        boolean limiteFrecuenciaFalta = frecuencia == null || (practica == null && maxima == null);

        if (programacion != null && limiteFrecuenciaFalta) {
            if (estacion == null) {
                faltantes.add("La programación del ciclo no indica la estación");
            } else if (frecuencia == null) {
                faltantes.add("No hay frecuencia configurada para la estación " + Py.str(estacion));
            } else {
                faltantes.add("La frecuencia de la estación " + Py.str(estacion) + " no tiene límite práctico ni máximo");
            }
        }

        String estadoFrecuencia;
        if (diasEntre == null) {
            estadoFrecuencia = SIN_CICLO_ANTERIOR;
            faltantes.add("No hay un ciclo anterior con fecha de inicio registrada");
        } else if (limiteFrecuenciaFalta) {
            estadoFrecuencia = LIMITE_NO_CONFIGURADO;
        } else {
            estadoFrecuencia = estadoFrecuencia(diasEntre, practica, maxima);
        }

        // --- Duración (reglas 7 a 9) ----------------------------------------
        LocalDate finCorte = Fechas.aFecha(ciclo.get("fecha_fin_corte"));
        LocalDate inicioLimpieza = Fechas.aFecha(ciclo.get("fecha_inicio_limpieza"));
        LocalDate finLimpieza = Fechas.aFecha(ciclo.get("fecha_fin_limpieza"));
        Object diasCorte = diasInclusivos(inicio, finCorte);
        Object diasLimpieza = diasInclusivos(inicioLimpieza, finLimpieza);

        if (finCorte == null) faltantes.add("Falta la fecha de fin de corte");
        if (inicioLimpieza == null) faltantes.add("Falta la fecha de inicio de limpieza");
        if (finLimpieza == null) faltantes.add("Falta la fecha de fin de limpieza");

        Map<String, Object> duracion = Py.truthy(programacion) ? ctx.duracion(tipoServicioId) : null;
        Object duracionMaxima = Py.truthy(duracion) ? duracion.get("duracion_maxima_dias") : null;
        Object limpiezaMaxima = Py.truthy(duracion) ? duracion.get("dias_limpieza") : null;

        String estadoDuracion;
        if (duracion == null || duracionMaxima == null) {
            estadoDuracion = LIMITE_NO_CONFIGURADO;
            if (programacion != null) faltantes.add("No hay duración máxima configurada para el servicio");
        } else if (diasCorte == null) {
            estadoDuracion = SIN_FECHA_FIN_CORTE;
        } else {
            boolean corteOk = Py.compare(diasCorte, duracionMaxima) <= 0;
            boolean limpiezaOk = diasLimpieza == null || limpiezaMaxima == null || Py.compare(diasLimpieza, limpiezaMaxima) <= 0;
            estadoDuracion = corteOk && limpiezaOk ? DENTRO_MAXIMO : EXCEDE_MAXIMO;
        }

        Object diasTotal = diasCorte != null ? Py.add(diasCorte, Py.or(diasLimpieza, 0L)) : null;
        Object maximoTotal = duracionMaxima != null ? Py.add(duracionMaxima, Py.or(limpiezaMaxima, 0L)) : null;

        Map<String, Object> proveedor = ctx.proveedores.get(Py.k(ciclo.get("proveedor_id")));
        Map<String, Object> ruta = ctx.rutas.get(Py.k(ciclo.get("ruta_id")));

        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("ciclo_id", Py.item(ciclo, "id"));
        fila.put("estado_ciclo", ciclo.get("estado"));
        fila.put("proveedor_id", ciclo.get("proveedor_id"));
        fila.put("proveedor_razon_social", Py.truthy(proveedor) ? proveedor.get("razon_social") : null);
        fila.put("ruta_id", ciclo.get("ruta_id"));
        fila.put("ruta_nombre", Py.truthy(ruta) ? ruta.get("nombre") : null);
        fila.put("estacion", estacion);
        fila.put("fecha_inicio_real", inicio);
        fila.put("ciclo_anterior_id", Py.truthy(anterior) ? Py.item(anterior, "id") : null);
        fila.put("fecha_inicio_anterior", inicioAnterior);
        fila.put("dias_entre_ciclos", diasEntre);
        fila.put("frecuencia_teorica_dias", teorica);
        fila.put("frecuencia_practica_dias", practica);
        fila.put("frecuencia_maxima_dias", maxima);
        fila.put("estado_frecuencia", estadoFrecuencia);
        fila.put("fecha_fin_corte", finCorte);
        fila.put("dias_corte", diasCorte);
        fila.put("fecha_inicio_limpieza", inicioLimpieza);
        fila.put("fecha_fin_limpieza", finLimpieza);
        fila.put("dias_limpieza", diasLimpieza);
        fila.put("dias_total", diasTotal);
        fila.put("duracion_maxima_dias", duracionMaxima);
        fila.put("dias_limpieza_maximo", limpiezaMaxima);
        fila.put("maximo_total", maximoTotal);
        fila.put("estado_duracion", estadoDuracion);
        fila.put("faltantes", faltantes);
        return fila;
    }

    private static Map<String, Object> contar(List<Map<String, Object>> filas, String atributo, List<String> valores) {
        Map<String, Object> conteo = new LinkedHashMap<>();
        for (String valor : valores) {
            long n = filas.stream().filter(f -> valor.equals(f.get(atributo))).count();
            conteo.put(valor, n);
        }
        return conteo;
    }
}
