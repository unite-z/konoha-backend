package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;
import static pe.edu.pucp.areasverdes.mod05.Mod05Repository.number;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReporteService {
    final Mod05Repository repo;
    final AtencionService atenciones;
    final ArchivoStorage storage;
    final Clock clock;
    final FormatosReporte formatos;
    final ObjectMapper json;

    /** Serializa los datos del informe con las claves ordenadas: la huella no depende del orden. */
    final ObjectMapper canonico;

    private static final Logger log = LoggerFactory.getLogger(ReporteService.class);

    public ReporteService(
            Mod05Repository repo,
            AtencionService atenciones,
            ArchivoStorage storage,
            Clock clock,
            FormatosReporte formatos,
            ObjectMapper json) {
        this.repo = repo;
        this.atenciones = atenciones;
        this.storage = storage;
        this.clock = clock;
        this.formatos = formatos;
        this.json = json;
        this.canonico = json.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    /** Sin datos del informe final: el comportamiento anterior a AV-320. */
    @Transactional
    public Map<String, Object> adjuntar(
            long id,
            LocalDate fecha,
            String resumen,
            List<Entradas.Documento> docs,
            List<MultipartFile> files,
            Usuario user) {
        return adjuntar(id, fecha, resumen, docs, files, null, user);
    }

    /**
     * Registra una revisión del reporte. {@code datosFormato} (JSON, opcional) son los datos del
     * informe final según el formato estándar vigente del tipo de servicio (RF-SER-009): si llega,
     * se valida entero contra ese formato y se guarda con él. Si no llega, la revisión se registra
     * sin datos, como antes, para no romper a los clientes que aún no los envían.
     */
    @Transactional
    public Map<String, Object> adjuntar(
            long id,
            LocalDate fecha,
            String resumen,
            List<Entradas.Documento> docs,
            List<MultipartFile> files,
            String datosFormato,
            Usuario user) {
        user.exigir(Usuario.REGISTRAR_ATENCION);
        var antes = repo.lock("atenciontercerizada", id);
        var a = Atencion.from(antes);
        // «Observado»: la nueva revisión es la subsanación y devuelve la atención a «Reporte
        // adjunto».
        a.exigir(Atencion.EJECUTADO, Atencion.REPORTE, Atencion.OBSERVADO);
        if (fecha.isBefore(a.fin().atZoneSameInstant(Mod05Application.LIMA).toLocalDate()))
            throw ApiError.invalid(
                    "La recepción del informe no puede preceder al fin del servicio");
        if (docs == null || docs.isEmpty() || docs.size() != files.size())
            throw ApiError.invalid("Debe existir un archivo por documento, en el mismo orden");
        if (docs.stream().noneMatch(d -> d.categoria().equals("InformeFinal")))
            throw ApiError.invalid("Cada revisión debe incluir un informe final");
        var datos = datosInforme(antes, datosFormato);
        var validated = files.stream().map(storage::validar).toList();
        for (var doc : docs) {
            if (doc.alcance_id() != null
                    && number(repo.get("alcanceatencion", doc.alcance_id()), "atencion_id") != id)
                throw ApiError.invalid(
                        "La evidencia debe corresponder a un alcance de esta atención");
            if (doc.categoria().equals("EvidenciaEspacio") && doc.alcance_id() == null)
                throw ApiError.invalid("La evidencia por espacio requiere indicar su alcance");
        }
        // Idempotencia: reenviar lo mismo que la última revisión (misma recepción, mismo
        // resumen y documentos con el mismo SHA-256) devuelve esa revisión y no crea nada.
        // Se evalúa bajo el bloqueo de la atención: dos envíos idénticos simultáneos
        // tampoco duplican.
        var ultima = ultimaRevision(id);
        if (ultima != null
                && huella(
                                ultima,
                                texto(ultima.get("formato_id")),
                                canonico(ultima.get("datos_formato")))
                        .equals(
                                huella(
                                        fecha,
                                        resumen,
                                        docs,
                                        validated,
                                        datos == null ? "" : String.valueOf(datos.formatoId()),
                                        datos == null ? "" : canonico(datos.valores())))) {
            ultima.put("repetido", true);
            return ultima;
        }
        if (Atencion.OBSERVADO.equals(a.estado()) && (resumen == null || resumen.isBlank()))
            throw ApiError.invalid("Indique cómo se subsanó la observación del informe");
        List<String> paths = new ArrayList<>();
        // Incluye fallos en el COMMIT, después de retornar de este método.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED)
                            paths.forEach(
                                    path -> {
                                        try {
                                            storage.borrar(path);
                                        } catch (Exception e) {
                                            log.error(
                                                    "No se pudo limpiar archivo huérfano {}",
                                                    path,
                                                    e);
                                        }
                                    });
                    }
                });
        int revision =
                repo.jdbc()
                        .queryForObject(
                                "SELECT coalesce(max(revision),0)+1 FROM mod05_reporteproveedor"
                                        + " WHERE atencion_id=:id",
                                Map.of("id", id),
                                Integer.class);
        OffsetDateTime now = OffsetDateTime.now(clock);
        long reporte =
                repo.insert(
                        "reporteproveedor",
                        values(
                                "atencion_id",
                                id,
                                "revision",
                                revision,
                                "fecha_recepcion",
                                fecha,
                                "resumen",
                                AtencionService.text(resumen),
                                "fecha_creacion",
                                now,
                                "fecha_modificacion",
                                now,
                                "usuario_creacion_id",
                                user.id(),
                                "usuario_modificacion_id",
                                user.id()));
        if (datos != null)
            repo.jdbc()
                    .update(
                            "UPDATE mod05_reporteproveedor SET formato_id=:formato,"
                                    + " datos_formato=CAST(:datos AS jsonb) WHERE id=:id",
                            Map.of(
                                    "formato",
                                    datos.formatoId(),
                                    "datos",
                                    escribir(datos.valores()),
                                    "id",
                                    reporte));
        for (int i = 0; i < docs.size(); i++) {
            var doc = docs.get(i);
            var file = validated.get(i);
            String key = "reportes/" + reporte + "/" + UUID.randomUUID() + "." + file.extension();
            paths.add(key);
            storage.guardar(key, file);
            long archivo =
                    repo.insert(
                            "archivo",
                            values(
                                    "nombre_original",
                                    file.nombre(),
                                    "tipo_mime",
                                    file.mime(),
                                    "tamano_bytes",
                                    file.size(),
                                    "hash_contenido",
                                    file.hash(),
                                    "ruta_almacenamiento",
                                    key));
            long asociado =
                    repo.insert(
                            "archivoasociado",
                            values(
                                    "reporte_id",
                                    reporte,
                                    "archivo_id",
                                    archivo,
                                    "categoria",
                                    doc.categoria(),
                                    "alcance_id",
                                    doc.alcance_id(),
                                    "descripcion",
                                    AtencionService.text(doc.descripcion())));
            repo.audit(
                    "ArchivoAsociado",
                    asociado,
                    "Alta",
                    user,
                    Map.of(),
                    repo.get("archivoasociado", asociado),
                    "");
        }
        var fila = repo.get("reporteproveedor", reporte);
        // jsonb llega como PGobject: en la auditoría va como el objeto que es.
        fila.computeIfPresent("datos_formato", (clave, valor) -> formatos.objeto(valor.toString()));
        repo.audit("ReporteProveedor", reporte, "Alta", user, Map.of(), fila, "");
        atenciones.guardar(id, values("estado", Atencion.REPORTE), antes, user, "");
        var creado = repo.reporte(id, reporte);
        creado.put("repetido", false);
        return creado;
    }

    /** Última revisión de la atención con sus documentos, o `null` si aún no tiene. */
    Map<String, Object> ultimaRevision(long id) {
        var filas =
                repo.rows(
                        Mod05Repository.REPORTES
                                + " WHERE r.atencion_id=:id ORDER BY r.revision DESC LIMIT 1",
                        Map.of("id", id));
        return filas.isEmpty() ? null : repo.enrichReport(filas.get(0));
    }

    /** Datos del informe final ya validados y el formato con el que se registran. */
    record DatosInforme(long formatoId, Map<String, Object> valores) {}

    /** Valida los datos del informe final contra el formato vigente de la atención. */
    DatosInforme datosInforme(Map<String, Object> atencion, String datosFormato) {
        if (datosFormato == null || datosFormato.isBlank()) return null;
        JsonNode nodo;
        try {
            nodo = json.readTree(datosFormato);
        } catch (JsonProcessingException e) {
            throw ApiError.invalid("datos_formato debe ser un objeto JSON válido");
        }
        var formato =
                formatos.vigente(Mod05Repository.number(atencion, "tipo_servicio_id"))
                        .orElseThrow(
                                () ->
                                        ApiError.invalid(
                                                "El tipo de servicio no tiene formato estándar"
                                                        + " vigente: adjunta el reporte sin los"
                                                        + " datos del informe final"));
        return new DatosInforme(
                formato.id(), DatosFormatoInforme.normalizar(formato.campos(), nodo));
    }

    private String escribir(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Datos del informe final no serializables", e);
        }
    }

    /** JSON con las claves ordenadas, o {@code ""} si no hay datos. */
    String canonico(Object valor) {
        if (valor == null) return "";
        try {
            return canonico.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Datos del informe final no serializables", e);
        }
    }

    static String texto(Object valor) {
        return valor == null ? "" : valor.toString().strip();
    }

    static String linea(String categoria, String alcance, String descripcion, String hash) {
        return String.join("\u001f", categoria, alcance, descripcion, hash);
    }

    /** Huella de una revisión ya guardada, sin datos del informe final. */
    static String huella(Map<String, Object> revision) {
        return huella(revision, "", "");
    }

    /** Huella de una revisión ya guardada; {@code datos} es el JSON canónico de sus datos. */
    @SuppressWarnings("unchecked")
    static String huella(Map<String, Object> revision, String formato, String datos) {
        var lineas = new ArrayList<String>();
        for (var doc : (List<Map<String, Object>>) revision.get("documentos")) {
            var archivo = (Map<String, Object>) doc.get("archivo");
            lineas.add(
                    linea(
                            texto(doc.get("categoria")),
                            texto(doc.get("alcance_id")),
                            texto(doc.get("descripcion")),
                            texto(archivo.get("hash_contenido"))));
        }
        return huella(
                        texto(revision.get("fecha_recepcion")),
                        texto(revision.get("resumen")),
                        lineas)
                + datos(formato, datos);
    }

    /** Huella de un envío sin datos del informe final. */
    static String huella(
            LocalDate fecha,
            String resumen,
            List<Entradas.Documento> docs,
            List<ArchivoStorage.Validado> files) {
        return huella(fecha, resumen, docs, files, "", "");
    }

    /**
     * Huella de un envío: no depende del orden de los documentos ni de los nombres de archivo. Los
     * datos del informe final entran como JSON canónico, con el formato en que se registran.
     */
    static String huella(
            LocalDate fecha,
            String resumen,
            List<Entradas.Documento> docs,
            List<ArchivoStorage.Validado> files,
            String formato,
            String datos) {
        var lineas = new ArrayList<String>();
        for (int i = 0; i < docs.size(); i++) {
            var d = docs.get(i);
            lineas.add(
                    linea(
                            d.categoria(),
                            texto(d.alcance_id()),
                            texto(d.descripcion()),
                            files.get(i).hash()));
        }
        return huella(fecha.toString(), texto(resumen), lineas) + datos(formato, datos);
    }

    /** Sin datos no añade nada: la huella de las revisiones sin datos no cambia. */
    static String datos(String formato, String datos) {
        return datos.isEmpty() ? "" : "\u001d" + formato + "\u001d" + datos;
    }

    static String huella(String fecha, String resumen, List<String> lineas) {
        return fecha
                + "\u001e"
                + resumen
                + "\u001e"
                + lineas.stream().sorted().collect(Collectors.joining("\u001e"));
    }
}
