package pe.edu.pucp.areasverdes.mod06.ia;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.comun.Py;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ConfiguracionFaltante;
import pe.edu.pucp.areasverdes.mod06.comun.errores.ParametroInvalido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.RecursoNoEncontrado;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TipoArchivoNoAdmitido;
import pe.edu.pucp.areasverdes.mod06.comun.errores.TransicionNoPermitida;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.FuenteDatos;
import pe.edu.pucp.areasverdes.mod06.fuentesdatos.SelectorFuenteDatos;

/**
 * Servicio de RF-IA-001: extracción asistida por IA de los informes de proveedores tercerizados.
 *
 * <p>Trazabilidad: RF-IA-001 · HU-MOD06-010 · CU-MOD06-010 · CP-TR-MOD06-010 · RN-IA-001 · RI-32 · CU-MOD05-006
 * (campos del reporte) · CU-MOD05-009 (formato estándar) · CU-MOD05-025 1a (validación de cada dato).
 *
 * <p>Texto del requisito (Catálogo v2): el sistema deberá permitir analizar mediante IA los reportes y documentos
 * entregados por los proveedores tercerizados para extraer y proponer información estructurada del servicio para su
 * revisión antes de incorporarla al registro oficial. La IA no registrará información de manera definitiva sin
 * validación humana.
 *
 * <p>Reglas transversales de IA (RN-IA-001):
 * <ul>
 *   <li>la salida de IA es una recomendación; la validación humana es obligatoria antes de cualquier efecto;</li>
 *   <li>el flujo convencional continúa si la IA no está disponible ({@code ServicioIANoDisponible} → registro
 *   manual);</li>
 *   <li>se distingue dato registrado, sugerencia ({@code Propuesta}) y decisión validada ({@code Validada} /
 *   {@code Rechazada});</li>
 *   <li>se conservan las entradas relevantes y la salida de IA (dentro de {@code SugerenciaIA.contenido});</li>
 *   <li>la función tiene métricas propias ({@link #metricas()}).</li>
 * </ul>
 *
 * <p>Estados (RI-32, patrón Estado simplificado del Diagrama de Clases v1, sección 7.1):
 * {@code Propuesta} → {@code Validada} | {@code Rechazada}. No hay otras transiciones.
 *
 * <p>Pendiente (Análisis de backend, brecha 2): proponer al responsable de BD (Nicolás Ipanaque) columnas propias
 * para entradas, modelo y decisiones por campo. Mientras tanto, se guardan dentro de {@code contenido}.
 *
 * <p>Estructura de {@code contenido} (versión 1):
 * <pre>
 * {
 *   "version_contenido": 1,
 *   "entrada": {reporte_proveedor_id, atencion_id, tipo_servicio_id, formato_id, formato_version, archivo_id,
 *               campos_solicitados: [{campo_id, nombre_campo, tipo_dato, obligatorio}]},
 *   "ia": {proveedor, modelo, solicitado_en, duracion_ms},
 *   "propuesta": {"campo_&lt;id&gt;": {nombre_campo, valor}},
 *   "campos_obligatorios_faltantes": ["campo_&lt;id&gt;"],
 *   "decision": null | {por_campo: {"campo_&lt;id&gt;": {accion, valor_final}},
 *                       resumen: {aceptados, corregidos, descartados}, motivo_rechazo}
 * }
 * </pre>
 */
@Service
public class ExtraccionReportesService {

    private static final Logger log = LoggerFactory.getLogger(ExtraccionReportesService.class);

    // --- Valores del Modelo de BD v1 ------------------------------------------------

    public static final String ENTIDAD_SUGERENCIA = "SugerenciaIA";
    public static final String TIPO_EXTRACCION = "ExtraccionReporte"; // TipoSugerenciaIA
    public static final String ENTIDAD_REPORTE = "ReporteProveedor";
    public static final String CATEGORIA_ARCHIVO_REPORTE = "ReporteProveedor"; // CategoriaArchivo
    public static final String MIME_PDF = "application/pdf";

    public static final String PROPUESTA = "Propuesta";
    public static final String VALIDADA = "Validada";
    public static final String RECHAZADA = "Rechazada";
    public static final List<String> ESTADOS_SUGERENCIA = List.of(PROPUESTA, VALIDADA, RECHAZADA);

    /** Transiciones permitidas (RI-32: «solo el validador cambia a Validada»). */
    public static final Map<String, Set<String>> TRANSICIONES_PERMITIDAS =
        Map.of(PROPUESTA, Set.of(VALIDADA, RECHAZADA), VALIDADA, Set.of(), RECHAZADA, Set.of());

    public static final Map<String, String> ETIQUETAS_ESTADO =
        Map.of(PROPUESTA, "Pendiente de revisión", VALIDADA, "Validada", RECHAZADA, "Rechazada");

    public static final String ACEPTAR = "aceptar";
    public static final String CORREGIR = "corregir";
    public static final String DESCARTAR = "descartar";
    public static final List<String> ACCIONES = List.of(ACEPTAR, CORREGIR, DESCARTAR);

    public static final long VERSION_CONTENIDO = 1;
    private static final ZonedDateTime INSTANTE_MINIMO = ZonedDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final String RUTA_INSTRUCCION = "prompts/extraccion_reporte_proveedor.md";

    // --- Mensajes -------------------------------------------------------------------

    public static final String MENSAJE_REPORTE_NO_EXISTE = "No existe el reporte del proveedor indicado; revisa el identificador";
    public static final String MENSAJE_ATENCION_NO_EXISTE =
        "El reporte no está asociado a una atención tercerizada existente; revisa el registro o registra los datos manualmente";
    public static final String MENSAJE_SIN_FORMATO =
        "El tipo de servicio no tiene formato estándar vigente; configúralo (RF-SER-009) o registra los datos manualmente";
    public static final String MENSAJE_FORMATO_SIN_CAMPOS =
        "El formato estándar vigente del servicio no tiene campos; configúralo (RF-SER-009) o registra los datos manualmente";
    public static final String MENSAJE_SIN_ARCHIVO = "El reporte no tiene informe adjunto; adjúntalo o registra los datos manualmente";
    public static final String MENSAJE_NO_PDF =
        "Solo se admite el informe en PDF; registra los datos manualmente o adjunta el informe en PDF";
    public static final String MENSAJE_SUGERENCIA_NO_EXISTE = "No existe la propuesta de IA indicada; revisa el identificador";

    // --- Esquema JSON por tipo de dato (tipos lógicos del Modelo de BD v1) ---------

    private static final Map<String, Map<String, Object>> ESQUEMA_POR_TIPO = new LinkedHashMap<>();

    static {
        ESQUEMA_POR_TIPO.put("Texto", Py.dict("type", List.of("string", "null")));
        ESQUEMA_POR_TIPO.put("Texto largo", Py.dict("type", List.of("string", "null")));
        ESQUEMA_POR_TIPO.put("Entero", Py.dict("type", List.of("integer", "null")));
        ESQUEMA_POR_TIPO.put("Decimal", Py.dict("type", List.of("number", "null")));
        ESQUEMA_POR_TIPO.put("Booleano", Py.dict("type", List.of("boolean", "null")));
        ESQUEMA_POR_TIPO.put("Fecha", Py.dict("type", List.of("string", "null"), "format", "date"));
        ESQUEMA_POR_TIPO.put("JSON", Py.dict("type", List.of("array", "null"), "items", Py.dict("type", "object")));
    }

    public static final List<String> TIPOS_DATO_ADMITIDOS = List.copyOf(ESQUEMA_POR_TIPO.keySet());

    private final SelectorFuenteDatos selector;
    private final FabricaClienteIA fabricaCliente;
    private final Clock reloj;
    private final SelectorFuenteReportesProveedor selectorReportes;

    public ExtraccionReportesService(SelectorFuenteDatos selector, FabricaClienteIA fabricaCliente, Clock reloj) {
        this(selector, fabricaCliente, reloj, null);
    }

    @Autowired
    public ExtraccionReportesService(SelectorFuenteDatos selector, FabricaClienteIA fabricaCliente, Clock reloj,
                                     SelectorFuenteReportesProveedor selectorReportes) {
        this.selector = selector;
        this.fabricaCliente = fabricaCliente;
        this.reloj = reloj;
        this.selectorReportes = selectorReportes;
    }

    private FuenteDatos fuente(FuenteDatos fuente) {
        return fuente != null ? fuente : selector.obtenerFuenteDatos();
    }

    /**
     * Fuente del reporte, su atención, su informe y sus bytes ({@code IA_FUENTE_REPORTES}). Si se pasa una
     * {@code FuenteDatos} explícita (pruebas), se usa esa, como antes.
     */
    private FuenteReportesProveedor reportes(FuenteDatos explicita, FuenteDatos f) {
        if (explicita != null || selectorReportes == null) return new FuenteReportesProveedorMock(f);
        return selectorReportes.obtener(f);
    }

    // =============================================================================
    // Operaciones públicas
    // =============================================================================

    public Map<String, Object> solicitarExtraccion(Object reporteId, Object usuarioId) {
        return solicitarExtraccion(reporteId, usuarioId, null, null);
    }

    /**
     * Analiza con IA el informe de un ReporteProveedor y crea una SugerenciaIA en estado {@code Propuesta}.
     *
     * <p>Pasos 2 a 6 del flujo (HU-MOD06-010). Si la IA falla, se propaga {@code ServicioIANoDisponible} y no se crea
     * ninguna sugerencia (el usuario continúa con el registro manual).
     */
    public Map<String, Object> solicitarExtraccion(Object reporteId, Object usuarioId, FuenteDatos fuente, ClienteIA cliente) {
        FuenteDatos f = fuente(fuente);
        FuenteReportesProveedor r = reportes(fuente, f);

        Map<String, Object> reporte = r.obtenerReporte(reporteId);
        if (reporte == null) throw new RecursoNoEncontrado(MENSAJE_REPORTE_NO_EXISTE);
        Map<String, Object> atencion = r.obtenerAtencion(reporte.get("atencion_id"));
        if (atencion == null) throw new RecursoNoEncontrado(MENSAJE_ATENCION_NO_EXISTE);
        Object tipoServicioId = atencion.get("tipo_servicio_id");

        Map<String, Object> formato = formatoVigente(f, tipoServicioId);
        List<Map<String, Object>> campos = new ArrayList<>(f.listar("CampoFormatoReporte", "formato_id", Py.item(formato, "id")));
        campos.sort((a, b) -> Py.compare(Py.item(a, "id"), Py.item(b, "id")));
        if (campos.isEmpty()) throw new ConfiguracionFaltante(MENSAJE_FORMATO_SIN_CAMPOS);
        Map<String, Object> esquema = construirEsquema(campos);

        Map<String, Object> archivo = archivoDelReporte(r, Py.item(reporte, "id"));
        if (!Py.eq(archivo.get("tipo_mime"), MIME_PDF)) throw new TipoArchivoNoAdmitido(MENSAJE_NO_PDF);

        // El cliente se obtiene antes de leer el archivo: si la IA no está configurada se informa de inmediato.
        ClienteIA c = cliente != null ? cliente : fabricaCliente.obtenerClienteIa();
        byte[] pdf = r.leerContenidoArchivo(archivo);
        String instruccion = leerInstruccion();

        String solicitadoEn = Fechas.ahoraIso(reloj);
        long inicio = System.nanoTime();
        Map<String, Object> salida = c.extraerJson(pdf, esquema, instruccion);
        long duracionMs = Py.roundEntero((System.nanoTime() - inicio) / 1_000_000.0);

        Map<String, Object> propuesta = new LinkedHashMap<>();
        for (Map<String, Object> campo : campos) {
            String clave = claveCampo(Py.item(campo, "id"));
            Object valor = salida.get(clave);
            if (valor != null && !valorEsDelTipo(valor, campo.get("tipo_dato"))) {
                log.warn("La IA propuso un valor de tipo no válido para {}; se deja sin valor", clave);
                valor = null;
            }
            propuesta.put(clave, Py.dict("nombre_campo", campo.get("nombre_campo"), "valor", valor));
        }

        List<Object> camposSolicitados = new ArrayList<>();
        for (Map<String, Object> campo : campos) {
            camposSolicitados.add(Py.dict(
                "campo_id", Py.item(campo, "id"),
                "nombre_campo", campo.get("nombre_campo"),
                "tipo_dato", campo.get("tipo_dato"),
                "obligatorio", Py.truthy(campo.get("obligatorio"))));
        }
        List<Object> obligatoriosFaltantes = new ArrayList<>();
        for (Map<String, Object> campo : campos) {
            String clave = claveCampo(Py.item(campo, "id"));
            if (Py.truthy(campo.get("obligatorio")) && sinValor(((Map<?, ?>) propuesta.get(clave)).get("valor"))) {
                obligatoriosFaltantes.add(clave);
            }
        }

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("version_contenido", VERSION_CONTENIDO);
        contenido.put("entrada", Py.dict(
            "reporte_proveedor_id", Py.item(reporte, "id"),
            "atencion_id", Py.item(atencion, "id"),
            "tipo_servicio_id", tipoServicioId,
            "formato_id", Py.item(formato, "id"),
            "formato_version", formato.get("version"),
            "archivo_id", Py.item(archivo, "id"),
            "campos_solicitados", camposSolicitados));
        contenido.put("ia", Py.dict(
            "proveedor", c.proveedor() != null ? c.proveedor() : ClienteGemini.PROVEEDOR_IA,
            "modelo", c.modelo(),
            "solicitado_en", solicitadoEn,
            "duracion_ms", duracionMs));
        contenido.put("propuesta", propuesta);
        contenido.put("campos_obligatorios_faltantes", obligatoriosFaltantes);
        contenido.put("decision", null);

        String instante = Fechas.ahoraIso(reloj);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", TIPO_EXTRACCION);
        datos.put("entidad", ENTIDAD_REPORTE);
        datos.put("registro_id", Py.item(reporte, "id"));
        datos.put("archivo_id", Py.item(archivo, "id"));
        datos.put("contenido", contenido);
        datos.put("estado", PROPUESTA);
        datos.put("validador_id", null);
        datos.put("fecha_validacion", null);
        datos.put("fecha_creacion", instante);
        datos.put("usuario_creacion_id", usuarioId);
        datos.put("fecha_modificacion", instante);
        datos.put("usuario_modificacion_id", usuarioId);
        Map<String, Object> sugerencia = f.crear(ENTIDAD_SUGERENCIA, datos);
        registrarAuditoria("Alta", sugerencia, usuarioId);
        return sugerencia;
    }

    public Map<String, Object> obtener(Object sugerenciaId) {
        return obtener(sugerenciaId, null);
    }

    /** Detalle de una sugerencia de extracción. {@code RecursoNoEncontrado} si no existe. */
    public Map<String, Object> obtener(Object sugerenciaId, FuenteDatos fuente) {
        FuenteDatos f = fuente(fuente);
        Map<String, Object> sugerencia = f.obtener(ENTIDAD_SUGERENCIA, sugerenciaId);
        if (sugerencia == null || !Py.eq(sugerencia.get("tipo"), TIPO_EXTRACCION)) {
            throw new RecursoNoEncontrado(MENSAJE_SUGERENCIA_NO_EXISTE);
        }
        return sugerencia;
    }

    public List<Map<String, Object>> listar(Object estado, Object reporteProveedorId) {
        return listar(estado, reporteProveedorId, null);
    }

    /** Sugerencias {@code ExtraccionReporte}, opcionalmente por estado y reporte, de la más reciente a la más antigua. */
    public List<Map<String, Object>> listar(Object estado, Object reporteProveedorId, FuenteDatos fuente) {
        FuenteDatos f = fuente(fuente);
        Map<String, Object> filtros = new LinkedHashMap<>();
        filtros.put("tipo", TIPO_EXTRACCION);
        if (estado != null) filtros.put("estado", estado);
        if (reporteProveedorId != null) {
            filtros.put("entidad", ENTIDAD_REPORTE);
            filtros.put("registro_id", reporteProveedorId);
        }
        List<Map<String, Object>> sugerencias = new ArrayList<>(f.listar(ENTIDAD_SUGERENCIA, filtros));
        sugerencias.sort((a, b) -> Py.compare(claveOrdenListado(b), claveOrdenListado(a)));
        return sugerencias;
    }

    private static List<Object> claveOrdenListado(Map<String, Object> s) {
        ZonedDateTime fecha = Fechas.aFechaHora(s.get("fecha_creacion"));
        return Arrays.asList(fecha != null ? fecha : INSTANTE_MINIMO, Py.or(s.get("id"), 0L));
    }

    public Map<String, Object> validar(Object sugerenciaId, Object usuarioId, Map<String, Object> decisiones) {
        return validar(sugerenciaId, usuarioId, decisiones, null);
    }

    /**
     * Revisión campo por campo (CU-MOD05-025 1a): {@code aceptar}, {@code corregir} (con {@code valor_final}) o
     * {@code descartar}.
     *
     * <p>SugerenciaIA.validar(usuario) del Diagrama de Clases v1. Solo desde {@code Propuesta} (RI-32).
     * Si todas las acciones son {@code descartar}, la sugerencia pasa a {@code Rechazada}; si no, a {@code Validada}
     * y se invoca la incorporación al registro oficial (solo lo aceptado o corregido se incorpora, HU-MOD06-010).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validar(Object sugerenciaId, Object usuarioId, Map<String, Object> decisiones, FuenteDatos fuente) {
        FuenteDatos f = fuente(fuente);
        Map<String, Object> sugerencia = obtener(sugerenciaId, f);
        comprobarTransicion(sugerencia, VALIDADA);

        Object contenidoObj = Py.or(sugerencia.get("contenido"), new LinkedHashMap<String, Object>());
        Map<String, Object> contenido = FuenteDatos.comoRegistro(contenidoObj);
        Map<String, Object> propuesta = FuenteDatos.comoRegistro(Py.or(contenido.get("propuesta"), new LinkedHashMap<String, Object>()));
        Map<String, Object> tipos = new LinkedHashMap<>();
        Object entrada = Py.or(contenido.get("entrada"), new LinkedHashMap<String, Object>());
        Object camposSolicitados = FuenteDatos.comoRegistro(entrada).containsKey("campos_solicitados")
            ? FuenteDatos.comoRegistro(entrada).get("campos_solicitados") : List.of();
        for (Object c : (Iterable<Object>) camposSolicitados) {
            tipos.put(claveCampo(Py.item(c, "campo_id")), Py.get(c, "tipo_dato"));
        }

        if (decisiones == null) throw new ParametroInvalido("Envía las decisiones como un objeto con una entrada por campo");
        Set<String> faltan = new TreeSet<>(Py::compararTexto);
        for (String clave : propuesta.keySet()) if (!decisiones.containsKey(clave)) faltan.add(clave);
        Set<String> sobran = new TreeSet<>(Py::compararTexto);
        for (String clave : decisiones.keySet()) if (!propuesta.containsKey(clave)) sobran.add(clave);
        if (!faltan.isEmpty() || !sobran.isEmpty()) {
            List<String> partes = new ArrayList<>();
            if (!faltan.isEmpty()) partes.add("faltan: " + String.join(", ", faltan));
            if (!sobran.isEmpty()) partes.add("sobran: " + String.join(", ", sobran));
            throw new ParametroInvalido(
                "Indica una decisión para cada campo de la propuesta, y solo para ellos (" + String.join("; ", partes) + ")");
        }

        Map<String, Object> porCampo = new LinkedHashMap<>();
        for (String clave : propuesta.keySet()) {
            Object decision = decisiones.get(clave);
            String nombre = Py.str(Py.or(Py.get(propuesta.get(clave), "nombre_campo"), clave));
            Object accion = decision instanceof Map<?, ?> d ? d.get("accion") : null;
            if (!(accion instanceof String a && ACCIONES.contains(a))) {
                throw new ParametroInvalido("La acción del campo '" + nombre + "' debe ser aceptar, corregir o descartar");
            }
            Object valorFinal;
            if (accion.equals(ACEPTAR)) {
                valorFinal = Py.get(propuesta.get(clave), "valor");
            } else if (accion.equals(CORREGIR)) {
                valorFinal = ((Map<?, ?>) decision).get("valor_final");
                if (valorFinal == null) {
                    throw new ParametroInvalido("Indica el valor final del campo '" + nombre + "' para corregirlo");
                }
                if (!valorEsDelTipo(valorFinal, tipos.get(clave))) {
                    throw new ParametroInvalido(
                        "El valor final del campo '" + nombre + "' debe ser de tipo " + Py.str(tipos.get(clave)));
                }
            } else {
                valorFinal = null;
            }
            porCampo.put(clave, Py.dict("accion", accion, "valor_final", valorFinal));
        }

        List<String> acciones = new ArrayList<>();
        for (Object d : porCampo.values()) acciones.add((String) ((Map<?, ?>) d).get("accion"));
        String nuevoEstado = !acciones.isEmpty() && acciones.stream().allMatch(DESCARTAR::equals) ? RECHAZADA : VALIDADA;
        comprobarTransicion(sugerencia, nuevoEstado);

        contenido.put("decision", Py.dict(
            "por_campo", porCampo,
            "resumen", resumenAcciones(acciones),
            "motivo_rechazo", null));
        Map<String, Object> actualizada = guardarRevision(f, sugerencia, nuevoEstado, contenido, usuarioId);
        if (nuevoEstado.equals(VALIDADA)) incorporarAlRegistroOficial(actualizada);
        return actualizada;
    }

    public Map<String, Object> rechazar(Object sugerenciaId, Object usuarioId, String motivo) {
        return rechazar(sugerenciaId, usuarioId, motivo, null);
    }

    /**
     * Rechaza la propuesta completa (SugerenciaIA.rechazar(usuario), Diagrama de Clases v1). Solo desde
     * {@code Propuesta}.
     *
     * <p>Todas las acciones quedan como {@code descartar} y se guarda {@code motivo_rechazo}.
     */
    public Map<String, Object> rechazar(Object sugerenciaId, Object usuarioId, String motivo, FuenteDatos fuente) {
        FuenteDatos f = fuente(fuente);
        Map<String, Object> sugerencia = obtener(sugerenciaId, f);
        comprobarTransicion(sugerencia, RECHAZADA);
        Map<String, Object> contenido = FuenteDatos.comoRegistro(Py.or(sugerencia.get("contenido"), new LinkedHashMap<String, Object>()));
        List<String> claves = new ArrayList<>(
            FuenteDatos.comoRegistro(Py.or(contenido.get("propuesta"), new LinkedHashMap<String, Object>())).keySet());
        Map<String, Object> porCampo = new LinkedHashMap<>();
        List<String> acciones = new ArrayList<>();
        for (String clave : claves) {
            porCampo.put(clave, Py.dict("accion", DESCARTAR, "valor_final", null));
            acciones.add(DESCARTAR);
        }
        contenido.put("decision", Py.dict(
            "por_campo", porCampo,
            "resumen", resumenAcciones(acciones),
            "motivo_rechazo", Py.or(motivo, null)));
        return guardarRevision(f, sugerencia, RECHAZADA, contenido, usuarioId);
    }

    public Map<String, Object> metricas() {
        return metricas(null);
    }

    /**
     * Métricas de la función de IA (reglas transversales: «métricas y escenarios de prueba propios»).
     *
     * <ul>
     *   <li>total por estado;</li>
     *   <li>campos aceptados sin cambios, corregidos y descartados;</li>
     *   <li>tasa de aceptación sin cambios = aceptados / (aceptados + corregidos + descartados), o {@code null};</li>
     *   <li>duración media de la llamada a la IA ({@code ia.duracion_ms}), o {@code null}.</li>
     * </ul>
     */
    public Map<String, Object> metricas(FuenteDatos fuente) {
        FuenteDatos f = fuente(fuente);
        List<Map<String, Object>> sugerencias = f.listar(ENTIDAD_SUGERENCIA, "tipo", TIPO_EXTRACCION);
        Map<String, Object> porEstado = new LinkedHashMap<>();
        for (String estado : ESTADOS_SUGERENCIA) porEstado.put(estado, 0L);
        long aceptados = 0, corregidos = 0, descartados = 0;
        List<Object> duraciones = new ArrayList<>();
        for (Map<String, Object> sugerencia : sugerencias) {
            Object estado = sugerencia.get("estado");
            if (estado instanceof String e && porEstado.containsKey(e)) porEstado.put(e, (Long) porEstado.get(e) + 1);
            Map<String, Object> contenido = FuenteDatos.comoRegistro(Py.or(sugerencia.get("contenido"), new LinkedHashMap<String, Object>()));
            Object duracion = Py.get(Py.or(contenido.get("ia"), new LinkedHashMap<String, Object>()), "duracion_ms");
            if (Py.esNumero(duracion)) duraciones.add(duracion);
            Map<String, Object> decision = FuenteDatos.comoRegistro(Py.or(contenido.get("decision"), new LinkedHashMap<String, Object>()));
            Object porCampo = Py.or(decision.get("por_campo"), new LinkedHashMap<String, Object>());
            for (Object valor : FuenteDatos.comoRegistro(porCampo).values()) {
                Object accion = Py.get(valor, "accion");
                if (ACEPTAR.equals(accion)) aceptados++;
                if (CORREGIR.equals(accion)) corregidos++;
                if (DESCARTAR.equals(accion)) descartados++;
            }
        }
        long totalDecisiones = aceptados + corregidos + descartados;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total_sugerencias", (long) sugerencias.size());
        r.put("por_estado", porEstado);
        r.put("campos_aceptados", aceptados);
        r.put("campos_corregidos", corregidos);
        r.put("campos_descartados", descartados);
        r.put("tasa_aceptacion_sin_cambios", totalDecisiones != 0 ? Py.round(Py.truediv(aceptados, totalDecisiones), 4) : null);
        r.put("duracion_media_ms", !duraciones.isEmpty()
            ? Py.round(Py.truediv(Py.sum(duraciones), (long) duraciones.size()), 1) : null);
        return r;
    }

    /** Rótulo para la interfaz: {@code Propuesta} → «Pendiente de revisión» (HU-MOD06-010; Guía Web 7). */
    public static Object etiquetaEstado(Object estado) {
        if (estado instanceof String e && ETIQUETAS_ESTADO.containsKey(e)) return ETIQUETAS_ESTADO.get(e);
        return estado;
    }

    // =============================================================================
    // Esquema y validación de tipos
    // =============================================================================

    /** Clave estable de un campo dentro de {@code propuesta}: {@code campo_<id del CampoFormatoReporte>}. */
    public static String claveCampo(Object campoId) {
        return "campo_" + Py.str(campoId);
    }

    /**
     * Esquema JSON pedido a Gemini: una propiedad {@code campo_<id>} por CampoFormatoReporte, todas admiten
     * {@code null}.
     *
     * <p>Un {@code tipo_dato} no admitido → {@link ConfiguracionFaltante} (422).
     */
    public static Map<String, Object> construirEsquema(List<Map<String, Object>> campos) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (Map<String, Object> campo : campos) {
            Object tipoDato = campo.get("tipo_dato");
            if (!(tipoDato instanceof String t && ESQUEMA_POR_TIPO.containsKey(t))) {
                throw new ConfiguracionFaltante("El campo '" + Py.str(campo.get("nombre_campo"))
                    + "' del formato tiene un tipo de dato no admitido; corrige el formato estándar del servicio (RF-SER-009)");
            }
            Map<String, Object> esquemaCampo = new LinkedHashMap<>(ESQUEMA_POR_TIPO.get(t));
            esquemaCampo.put("description", Py.str(Py.or(campo.get("nombre_campo"), "")));
            if (t.equals("JSON") && campo.get("subcampos") != null) {
                esquemaCampo.put("items", esquemaSubcampos(campo));
            }
            propiedades.put(claveCampo(Py.item(campo, "id")), esquemaCampo);
        }
        Map<String, Object> esquema = new LinkedHashMap<>();
        esquema.put("type", "object");
        esquema.put("properties", propiedades);
        esquema.put("required", new ArrayList<>(propiedades.keySet()));
        return esquema;
    }

    /**
     * Esquema de cada elemento de una lista JSON cuyo campo define {@code subcampos}
     * ({@code [{"nombre", "tipo_dato", "descripcion"}]}). Sin subcampos, el elemento es un objeto libre
     * ({@code {"type": "object"}}) y Gemini tiende a devolverlo vacío. Los subcampos admiten cualquier tipo salvo
     * {@code JSON} y todos aceptan {@code null}. Un subcampo mal definido → {@link ConfiguracionFaltante} (422).
     */
    private static Map<String, Object> esquemaSubcampos(Map<String, Object> campo) {
        String errorFormato = "Los subcampos del campo '" + Py.str(campo.get("nombre_campo"))
            + "' del formato no son válidos; corrige el formato estándar del servicio (RF-SER-009)";
        if (!(campo.get("subcampos") instanceof List<?> subcampos) || subcampos.isEmpty()) {
            throw new ConfiguracionFaltante(errorFormato);
        }
        Map<String, Object> propiedades = new LinkedHashMap<>();
        for (Object s : subcampos) {
            if (!(s instanceof Map<?, ?> sub) || !(sub.get("nombre") instanceof String nombre) || nombre.isBlank()
                || !(sub.get("tipo_dato") instanceof String tipo) || tipo.equals("JSON") || !ESQUEMA_POR_TIPO.containsKey(tipo)
                || propiedades.containsKey(nombre)) {
                throw new ConfiguracionFaltante(errorFormato);
            }
            Map<String, Object> esquemaSub = new LinkedHashMap<>(ESQUEMA_POR_TIPO.get(tipo));
            esquemaSub.put("description", sub.get("descripcion") instanceof String d && !d.isBlank() ? d : nombre);
            propiedades.put(nombre, esquemaSub);
        }
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "object");
        items.put("properties", propiedades);
        items.put("required", new ArrayList<>(propiedades.keySet()));
        return items;
    }

    /** {@code true} si {@code valor} (no nulo) corresponde al tipo lógico {@code tipoDato} del Modelo de BD v1. */
    public static boolean valorEsDelTipo(Object valor, Object tipoDato) {
        if ("Texto".equals(tipoDato) || "Texto largo".equals(tipoDato)) return valor instanceof String;
        if ("Entero".equals(tipoDato)) return Py.esEntero(valor) && !(valor instanceof Boolean);
        if ("Decimal".equals(tipoDato)) return Py.esNumero(valor) && !(valor instanceof Boolean);
        if ("Booleano".equals(tipoDato)) return valor instanceof Boolean;
        if ("Fecha".equals(tipoDato)) {
            if (!(valor instanceof String s) || Py.len(s) != 10) return false;
            try {
                Fechas.fromIsoFecha(s);
            } catch (Py.ValueError e) {
                return false;
            }
            return true;
        }
        if ("JSON".equals(tipoDato)) {
            if (!(valor instanceof List<?> lista)) return false;
            for (Object elemento : lista) if (!(elemento instanceof Map<?, ?>)) return false;
            return true;
        }
        return false;
    }

    // =============================================================================
    // Puntos de integración con otros módulos
    // =============================================================================

    /**
     * Registro de auditoría de la SugerenciaIA (acciones del enumerado AccionAuditoria: Alta, Modificacion,
     * CambioEstado).
     *
     * <p>TODO: delegar en RegistradorAuditoria (MOD-01, RF-T-008).
     */
    private static void registrarAuditoria(String accion, Map<String, Object> sugerencia, Object usuarioId) {
        log.info("Auditoría: {} de SugerenciaIA id={} estado={} usuario_id={}", accion, Py.str(sugerencia.get("id")),
            Py.str(sugerencia.get("estado")), Py.str(usuarioId));
    }

    /**
     * Incorporación de los valores validados al registro oficial del MOD-05 (ReporteProveedor, AlcanceAtencion,
     * AplicacionObjetivo, AplicacionProducto, AplicacionPlaga). El MOD-06 no escribe en esas entidades (Modelo de BD
     * v1, sección 2.1): los valores quedan en la SugerenciaIA {@code Validada}, disponibles por la API.
     *
     * <p>TODO: invocar el servicio de MOD-05 cuando publique su contrato (coordinar con Emely Munayco y Mafer Ramos).
     */
    private static void incorporarAlRegistroOficial(Map<String, Object> sugerencia) {
        log.info("SugerenciaIA id={} validada: valores disponibles para que el MOD-05 los incorpore al registro oficial",
            Py.str(sugerencia.get("id")));
    }

    // =============================================================================
    // Utilidades internas
    // =============================================================================

    /** Instrucción del sistema: copia literal de {@code ia/prompts/extraccion_reporte_proveedor.md}. */
    static String leerInstruccion() {
        try (InputStream entrada = ExtraccionReportesService.class.getClassLoader().getResourceAsStream(RUTA_INSTRUCCION)) {
            if (entrada == null) throw new IllegalStateException("Falta classpath:" + RUTA_INSTRUCCION);
            String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            // Modo texto de Python: saltos de línea universales.
            return texto.replace("\r\n", "\n").replace('\r', '\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Object> formatoVigente(FuenteDatos fuente, Object tipoServicioId) {
        List<Map<String, Object>> formatos = fuente.listar("FormatoReporteServicio", "tipo_servicio_id", tipoServicioId, "vigente", true);
        if (tipoServicioId == null || formatos.isEmpty()) throw new ConfiguracionFaltante(MENSAJE_SIN_FORMATO);
        Map<String, Object> mejor = null;
        List<Object> claveMejor = null;
        for (Map<String, Object> f : formatos) {
            List<Object> clave = Arrays.asList(Py.or(f.get("version"), 0L), Py.item(f, "id"));
            if (mejor == null || Py.compare(clave, claveMejor) > 0) {
                mejor = f;
                claveMejor = clave;
            }
        }
        return mejor;
    }

    private static Map<String, Object> archivoDelReporte(FuenteReportesProveedor fuente, Object reporteId) {
        List<Map<String, Object>> asociaciones = new ArrayList<>(fuente.archivosAsociados(reporteId, CATEGORIA_ARCHIVO_REPORTE));
        asociaciones.sort((a, b) -> Py.compare(Py.item(b, "id"), Py.item(a, "id")));
        for (Map<String, Object> asociacion : asociaciones) {
            Map<String, Object> archivo = fuente.obtenerArchivo(asociacion.get("archivo_id"));
            if (archivo != null) return archivo;
        }
        throw new RecursoNoEncontrado(MENSAJE_SIN_ARCHIVO);
    }

    private static boolean sinValor(Object valor) {
        return valor == null || Py.eq(valor, "") || Py.eq(valor, List.of());
    }

    private static void comprobarTransicion(Map<String, Object> sugerencia, String destino) {
        Object estado = sugerencia.get("estado");
        Set<String> permitidas = estado instanceof String e && TRANSICIONES_PERMITIDAS.containsKey(e)
            ? TRANSICIONES_PERMITIDAS.get(e) : Set.of();
        if (!permitidas.contains(destino)) {
            throw new TransicionNoPermitida("Esta propuesta ya fue revisada (estado actual: " + Py.str(estado) + ")");
        }
    }

    private static Map<String, Object> resumenAcciones(List<String> acciones) {
        return Py.dict(
            "aceptados", acciones.stream().filter(ACEPTAR::equals).count(),
            "corregidos", acciones.stream().filter(CORREGIR::equals).count(),
            "descartados", acciones.stream().filter(DESCARTAR::equals).count());
    }

    private Map<String, Object> guardarRevision(FuenteDatos fuente, Map<String, Object> sugerencia, String nuevoEstado,
                                                Map<String, Object> contenido, Object usuarioId) {
        String instante = Fechas.ahoraIso(reloj);
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("estado", nuevoEstado);
        cambios.put("validador_id", usuarioId);
        cambios.put("fecha_validacion", instante);
        cambios.put("contenido", contenido);
        cambios.put("fecha_modificacion", instante);
        cambios.put("usuario_modificacion_id", usuarioId);
        Map<String, Object> actualizada = fuente.actualizar(ENTIDAD_SUGERENCIA, Py.item(sugerencia, "id"), cambios);
        registrarAuditoria("CambioEstado", actualizada, usuarioId);
        return actualizada;
    }
}
