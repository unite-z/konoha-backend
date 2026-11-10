package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod06.comun.Fechas;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteGemini;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.comun.AlmacenSugerencias;

/**
 * Núcleo común de las funciones de IA de S12 (RF-IA-002 a RF-IA-006 y RN-IA-002), con el contrato
 * de {@code SugerenciaIAGenerica} de {@code konoha-frontend/src/types/ia.ts}: cada recurso {@code
 * ia/<recurso>/} tiene listado, solicitud, detalle, validación y rechazo, y solo cambia el {@code
 * contenido}.
 *
 * <p>Regla de dominio (RN-IA-001): la sugerencia nunca cambia un registro oficial por sí sola.
 * Validar o rechazar solo es posible desde {@code Propuesta} (409 {@code transicion-no-permitida});
 * la fila queda bloqueada durante la revisión, y lo que el módulo dueño escriba al validar va en la
 * misma transacción que la decisión.
 */
@Service
public class SugerenciasIa {

    public static final String PROPUESTA = "Propuesta";
    public static final String VALIDADA = "Validada";
    public static final String RECHAZADA = "Rechazada";
    public static final List<String> ESTADOS = List.of(PROPUESTA, VALIDADA, RECHAZADA);
    private static final Map<String, String> ETIQUETAS =
            Map.of(
                    PROPUESTA,
                    "Pendiente de revisión",
                    VALIDADA,
                    "Validada",
                    RECHAZADA,
                    "Rechazada");

    public static final String PROPONER = "IA.PROPUESTA.PROPONER";
    public static final String VALIDAR = "IA.PROPUESTA.VALIDAR";

    /** Resultado de una revisión: nuevo estado, contenido con su {@code decision} y motivo. */
    public record Revision(String estado, Map<String, Object> contenido, String motivo) {}

    /** Página de DRF: {@code {count, next, previous, results}}. */
    public record Pagina(
            long count, String next, String previous, List<Map<String, Object>> results) {}

    private final AlmacenSugerencias almacen;
    private final Clock reloj;

    public SugerenciasIa(AlmacenSugerencias almacen, Clock reloj) {
        this.almacen = almacen;
        this.reloj = reloj;
    }

    // ------------------------------------------------------------------ permisos

    public static void puedeConsultar(UsuarioSesion u) {
        exigir(u, "REP.INDICADOR.CONSULTAR");
    }

    public static void puedeProponer(UsuarioSesion u) {
        exigir(u, PROPONER);
    }

    public static void puedeValidar(UsuarioSesion u) {
        exigir(u, VALIDAR);
    }

    private static void exigir(UsuarioSesion u, String permiso) {
        if (u == null || !u.tiene(permiso)) throw ErrorGestion.sinPermiso();
    }

    // ------------------------------------------------------------------ operaciones

    /** Guarda una sugerencia nueva en {@code Propuesta}. */
    @Transactional
    public Map<String, Object> crear(
            String tipo,
            String entidad,
            Long registroId,
            Map<String, Object> contenido,
            long usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", tipo);
        datos.put("entidad", entidad);
        datos.put("registro_id", registroId);
        datos.put("archivo_id", null);
        datos.put("contenido", contenido);
        datos.put("fecha_creacion", Fechas.ahoraIso(reloj));
        datos.put("usuario_creacion_id", usuario);
        return vista(almacen.crear(datos));
    }

    /**
     * Guarda una sugerencia ya revisada (los caminos «manual»: sin IA, el usuario registra la
     * decisión directamente). Queda en {@code Validada} con su auditoría.
     */
    @Transactional
    public Map<String, Object> crearValidada(
            String tipo,
            String entidad,
            Long registroId,
            Map<String, Object> contenido,
            long usuario) {
        Map<String, Object> creada = crear(tipo, entidad, registroId, contenido, usuario);
        return revisar(
                ((Number) creada.get("id")).longValue(),
                tipo,
                usuario,
                c -> new Revision(VALIDADA, contenido, null));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(long id, String tipo) {
        return vista(delTipo(almacen.obtener(id), tipo));
    }

    @Transactional(readOnly = true)
    public Pagina listar(String tipo, String estado, String page, Paginacion.EnlacePagina enlace) {
        if (estado != null && !ESTADOS.contains(estado)) {
            throw ErrorGestion.validacion(
                    "estado", "Usa uno de estos valores: " + String.join(", ", ESTADOS));
        }
        List<Map<String, Object>> todas = almacen.listar(tipo, estado, null, null);
        long pagina = Paginacion.resolver(page, todas.size());
        long ultima = Paginacion.paginas(todas.size());
        List<Map<String, Object>> resultados = new ArrayList<>();
        todas.stream()
                .skip(Paginacion.desplazamiento(pagina))
                .limit(Paginacion.TAMANO_PAGINA)
                .forEach(s -> resultados.add(vista(s)));
        return new Pagina(
                todas.size(),
                pagina < ultima ? enlace.a(pagina + 1) : null,
                pagina > 1 ? enlace.a(pagina - 1) : null,
                resultados);
    }

    /**
     * Revisión de una propuesta: bloquea la fila, comprueba el tipo y el estado, aplica {@code
     * decidir} (que puede escribir en el módulo dueño) y guarda la decisión, todo en una
     * transacción. Si {@code decidir} falla, no se guarda nada y la sugerencia sigue en {@code
     * Propuesta}.
     */
    @Transactional
    public Map<String, Object> revisar(
            long id, String tipo, long usuario, Function<Map<String, Object>, Revision> decidir) {
        Map<String, Object> s = delTipo(almacen.obtenerParaRevisar(id), tipo);
        if (!PROPUESTA.equals(s.get("estado"))) {
            throw new ErrorGestion(
                    409,
                    "transicion-no-permitida",
                    "Esta propuesta ya fue revisada (estado actual: " + s.get("estado") + ")");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> contenido =
                s.get("contenido") instanceof Map<?, ?> m
                        ? new LinkedHashMap<>((Map<String, Object>) m)
                        : new LinkedHashMap<>();
        Revision r = decidir.apply(contenido);
        String ahora = Fechas.ahoraIso(reloj);
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("estado", r.estado());
        cambios.put("validador_id", usuario);
        cambios.put("fecha_validacion", ahora);
        cambios.put("contenido", r.contenido());
        cambios.put("fecha_modificacion", ahora);
        cambios.put("usuario_modificacion_id", usuario);
        return vista(almacen.guardarRevision(id, cambios, r.motivo()));
    }

    /** Rechazo sin más efectos: guarda {@code decision} con el motivo. */
    @Transactional
    public Map<String, Object> rechazar(
            long id,
            String tipo,
            long usuario,
            String motivo,
            Function<Map<String, Object>, Map<String, Object>> decisionDeRechazo) {
        if (motivo != null && motivo.length() > 1000) {
            throw ErrorGestion.validacion(
                    "motivo", "Asegúrate de que el motivo no tenga más de 1000 caracteres");
        }
        return revisar(
                id,
                tipo,
                usuario,
                c -> {
                    c.put("decision", decisionDeRechazo.apply(c));
                    return new Revision(RECHAZADA, c, motivo);
                });
    }

    // ------------------------------------------------------------------ ayudas

    /** Bloque {@code ia} de {@code MetadatosIA}. */
    public Map<String, Object> llamada(ClienteIA cliente, String solicitadoEn, long duracionMs) {
        Map<String, Object> ia = new LinkedHashMap<>();
        ia.put(
                "proveedor",
                cliente != null && cliente.proveedor() != null
                        ? cliente.proveedor()
                        : ClienteGemini.PROVEEDOR_IA);
        ia.put("modelo", cliente == null ? null : cliente.modelo());
        ia.put("solicitado_en", solicitadoEn);
        ia.put("duracion_ms", duracionMs);
        return ia;
    }

    public String ahora() {
        return Fechas.ahoraIso(reloj);
    }

    public Clock reloj() {
        return reloj;
    }

    /** {@code {aceptados, corregidos, descartados}} de una lista de acciones. */
    public static Map<String, Object> resumen(List<String> acciones) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("aceptados", acciones.stream().filter("aceptar"::equals).count());
        r.put("corregidos", acciones.stream().filter("corregir"::equals).count());
        r.put("descartados", acciones.stream().filter("descartar"::equals).count());
        return r;
    }

    private static Map<String, Object> delTipo(Map<String, Object> s, String tipo) {
        if (s == null || !tipo.equals(s.get("tipo"))) {
            throw new ErrorGestion(
                    404, "sugerencia-no-encontrada", "No existe la propuesta de IA indicada");
        }
        return s;
    }

    /** La sugerencia con su {@code etiqueta_estado}, como el serializador de RF-IA-001. */
    static Map<String, Object> vista(Map<String, Object> s) {
        Map<String, Object> r = new LinkedHashMap<>(s);
        r.put("etiqueta_estado", ETIQUETAS.get(String.valueOf(s.get("estado"))));
        return r;
    }
}
