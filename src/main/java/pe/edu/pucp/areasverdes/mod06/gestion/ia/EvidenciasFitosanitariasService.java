package pe.edu.pucp.areasverdes.mod06.gestion.ia;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.areasverdes.mod01.servicios.UsuarioSesion;
import pe.edu.pucp.areasverdes.mod05.ArchivoStorage;
import pe.edu.pucp.areasverdes.mod06.comun.JsonPy;
import pe.edu.pucp.areasverdes.mod06.gestion.ErrorGestion;
import pe.edu.pucp.areasverdes.mod06.gestion.servicios.Paginacion;
import pe.edu.pucp.areasverdes.mod06.ia.ClienteIA;
import pe.edu.pucp.areasverdes.mod06.ia.FabricaClienteIA;

/**
 * RF-IA-006: la IA analiza fotografías de una planta y sugiere hipótesis de afectación
 * fitosanitaria con su nivel de confianza. No hay diagnóstico hasta la validación técnica y nunca
 * se indica un tratamiento: si la IA lo propone, se descarta antes de guardar.
 *
 * <p>Fotos: de 1 a 5, JPG, PNG o WEBP (por su firma, no por la extensión), hasta 10 MB cada una. Se
 * guardan con {@link ArchivoStorage} bajo {@code mod06/evidencias/…} y se sirven solo por {@code
 * {id}/fotografias/{archivo_id}/} con sesión. Calidad insuficiente no es un error: 201 con {@code
 * calidad_suficiente: false} e {@code hipotesis: []}. Validar no escribe en otros módulos.
 */
@Service
public class EvidenciasFitosanitariasService {

    public static final String TIPO = "EvidenciaFitosanitaria";
    static final long MAXIMO_BYTES = 10L * 1024 * 1024;
    static final int MAXIMO_FOTOS = 5;
    static final List<String> CATEGORIAS =
            List.of(
                    "Plaga",
                    "Enfermedad",
                    "DanoMecanico",
                    "EstresHidrico",
                    "DeficienciaNutricional",
                    "Otra");
    static final String RUTA_INSTRUCCION = "prompts/evidencia-fitosanitaria.md";

    private static final Map<String, Object> ESQUEMA =
            PriorizacionesService.esquema(
                    Map.of(
                            "fotografias",
                                    Map.of(
                                            "type",
                                            "array",
                                            "items",
                                            PriorizacionesService.esquemaObjeto(
                                                    Map.of(
                                                            "indice", Map.of("type", "integer"),
                                                            "calidad",
                                                                    Map.of(
                                                                            "type",
                                                                            "string",
                                                                            "enum",
                                                                            List.of(
                                                                                    "Suficiente",
                                                                                    "Insuficiente")),
                                                            "motivo_calidad",
                                                                    Map.of(
                                                                            "type",
                                                                            List.of(
                                                                                    "string",
                                                                                    "null"))))),
                            "hipotesis",
                                    Map.of(
                                            "type",
                                            "array",
                                            "items",
                                            PriorizacionesService.esquemaObjeto(
                                                    Map.of(
                                                            "categoria",
                                                                    Map.of(
                                                                            "type",
                                                                            "string",
                                                                            "enum",
                                                                            CATEGORIAS),
                                                            "nombre", Map.of("type", "string"),
                                                            "confianza",
                                                                    Map.of(
                                                                            "type",
                                                                            List.of(
                                                                                    "number",
                                                                                    "null")),
                                                            "nivel_confianza",
                                                                    Map.of(
                                                                            "type",
                                                                            "string",
                                                                            "enum",
                                                                            List.of(
                                                                                    "Alta",
                                                                                    "Media",
                                                                                    "Baja",
                                                                                    "Incierta")),
                                                            "evidencias",
                                                                    Map.of(
                                                                            "type",
                                                                            "array",
                                                                            "items",
                                                                            Map.of(
                                                                                    "type",
                                                                                    "string"))))),
                            "explicacion", Map.of("type", "string"),
                            "limitaciones",
                                    Map.of("type", "array", "items", Map.of("type", "string"))));

    private final SugerenciasIa sugerencias;
    private final FabricaClienteIA fabrica;
    private final NamedParameterJdbcTemplate jdbc;
    private final ArchivoStorage almacenamiento;

    public EvidenciasFitosanitariasService(
            SugerenciasIa sugerencias,
            FabricaClienteIA fabrica,
            NamedParameterJdbcTemplate jdbc,
            ArchivoStorage almacenamiento) {
        this.sugerencias = sugerencias;
        this.fabrica = fabrica;
        this.jdbc = jdbc;
        this.almacenamiento = almacenamiento;
    }

    // ------------------------------------------------------------------ solicitar

    public Map<String, Object> solicitar(
            String datosJson, List<MultipartFile> archivos, long usuario) {
        Map<?, ?> datos = datos(datosJson);
        Long ejemplarId = datos.get("ejemplar_id") instanceof Number n ? n.longValue() : null;
        Long atencionId = datos.get("atencion_id") instanceof Number n ? n.longValue() : null;
        List<MultipartFile> fotos =
                archivos == null ? List.of() : archivos.stream().filter(f -> !f.isEmpty()).toList();
        if ((ejemplarId == null && atencionId == null) || fotos.isEmpty()) {
            throw new ErrorGestion(
                    400,
                    "datos-invalidos",
                    "Indica el ejemplar o la atención y adjunta al menos una fotografía");
        }
        if (fotos.size() > MAXIMO_FOTOS) {
            throw new ErrorGestion(
                    400,
                    "datos-invalidos",
                    "Adjunta como máximo " + MAXIMO_FOTOS + " fotografías",
                    Map.of("fotografias", List.of("Máximo " + MAXIMO_FOTOS)));
        }
        List<ClienteIA.Imagen> imagenes = new ArrayList<>();
        List<Map<String, Object>> fotografias = new ArrayList<>();
        List<byte[]> contenidos = new ArrayList<>();
        for (MultipartFile f : fotos) {
            byte[] bytes;
            try {
                bytes = f.getBytes();
            } catch (IOException e) {
                throw new ErrorGestion(400, "datos-invalidos", "No se pudo leer una fotografía");
            }
            String mime = tipoImagen(bytes);
            if (mime == null) {
                throw new ErrorGestion(
                        415, "tipo-no-admitido", "Solo se admiten fotografías JPG, PNG o WEBP");
            }
            if (bytes.length > MAXIMO_BYTES) {
                throw new ErrorGestion(
                        400,
                        "datos-invalidos",
                        "Cada fotografía puede pesar hasta 10 MB",
                        Map.of("fotografias", List.of("Hasta 10 MB")));
            }
            imagenes.add(new ClienteIA.Imagen(bytes, mime));
            contenidos.add(bytes);
            Map<String, Object> foto = new LinkedHashMap<>();
            foto.put("archivo_id", fotografias.size() + 1);
            foto.put(
                    "nombre",
                    f.getOriginalFilename() == null ? "fotografia" : f.getOriginalFilename());
            foto.put("tipo_mime", mime);
            fotografias.add(foto);
        }
        Map<String, Object> ejemplar =
                ejemplarId == null ? ejemplarDeAtencion(atencionId) : ejemplar(ejemplarId);
        Map<String, Object> atencion = atencionId == null ? null : atencion(atencionId);
        if (ejemplarId != null && ejemplar == null)
            throw new ErrorGestion(404, "ejemplar-no-encontrado", "No existe el ejemplar indicado");
        if (atencionId != null && atencion == null)
            throw new ErrorGestion(404, "atencion-no-encontrada", "No existe la atención indicada");
        String sintomas =
                datos.get("sintomas") instanceof String s && !s.isBlank() ? s.strip() : null;

        ClienteIA cliente = fabrica.obtenerClienteIa();
        List<String> plagas =
                jdbc.queryForList(
                        "SELECT nombre FROM mod05_plaga WHERE activo ORDER BY nombre",
                        Map.of(),
                        String.class);
        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("especie", ejemplar == null ? null : ejemplar.get("especie"));
        entrada.put("espacio", ejemplar == null ? null : ejemplar.get("espacio"));
        entrada.put("sintomas", sintomas);
        entrada.put("numero_fotografias", fotografias.size());
        entrada.put("catalogo_plagas", plagas);
        String solicitadoEn = sugerencias.ahora();
        long inicio = System.nanoTime();
        Map<String, Object> salida =
                cliente.generarJson(
                        JsonPy.dumpsRespuesta(entrada),
                        imagenes,
                        ESQUEMA,
                        PriorizacionesService.Prompts.leer(RUTA_INSTRUCCION));
        long duracion = (System.nanoTime() - inicio) / 1_000_000;

        // Calidad por foto (la IA las recibe en orden).
        boolean suficiente = true;
        Map<Integer, Map<?, ?>> calidades = new LinkedHashMap<>();
        if (salida.get("fotografias") instanceof List<?> l) {
            for (Object o : l)
                if (o instanceof Map<?, ?> m && m.get("indice") instanceof Number n)
                    calidades.put(n.intValue(), m);
        }
        for (int i = 0; i < fotografias.size(); i++) {
            Map<?, ?> c = calidades.getOrDefault(i, Map.of());
            boolean ok = !"Insuficiente".equals(c.get("calidad"));
            suficiente &= ok;
            fotografias.get(i).put("calidad", ok ? "Suficiente" : "Insuficiente");
            fotografias
                    .get(i)
                    .put(
                            "motivo_calidad",
                            ok
                                    ? null
                                    : c.get("motivo_calidad") instanceof String m
                                            ? m
                                            : "Imagen poco nítida");
        }
        List<Map<String, Object>> hipotesis = new ArrayList<>();
        if (suficiente && salida.get("hipotesis") instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> h)
                        || !(h.get("categoria") instanceof String cat)
                        || !CATEGORIAS.contains(cat)) continue;
                // Solo los campos del contrato: un «tratamiento» u otra clave de la IA no se
                // guarda.
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("clave", "h" + (hipotesis.size() + 1));
                x.put("categoria", cat);
                String nombre = h.get("nombre") instanceof String n ? n : cat;
                x.put("nombre", nombre);
                x.put("plaga_id", cat.equals("Plaga") ? plagaId(nombre) : null);
                x.put(
                        "confianza",
                        h.get("confianza") instanceof Number c
                                ? Math.max(0, Math.min(1, c.doubleValue()))
                                : null);
                x.put(
                        "nivel_confianza",
                        h.get("nivel_confianza") instanceof String nc
                                        && List.of("Alta", "Media", "Baja", "Incierta").contains(nc)
                                ? nc
                                : "Incierta");
                x.put(
                        "evidencias",
                        h.get("evidencias") instanceof List<?> ev
                                ? ev.stream().map(String::valueOf).toList()
                                : List.of());
                hipotesis.add(x);
            }
        }

        // Fotos al almacenamiento solo cuando la IA respondió: un 503 no deja archivos sueltos.
        for (int i = 0; i < fotografias.size(); i++) {
            Map<String, Object> foto = fotografias.get(i);
            String extension =
                    switch ((String) foto.get("tipo_mime")) {
                        case "image/png" -> "png";
                        case "image/webp" -> "webp";
                        default -> "jpg";
                    };
            String clave = "mod06/evidencias/" + UUID.randomUUID() + "." + extension;
            byte[] bytes = contenidos.get(i);
            almacenamiento.guardar(
                    clave,
                    new ArchivoStorage.Validado(
                            (String) foto.get("nombre"),
                            (String) foto.get("tipo_mime"),
                            bytes.length,
                            sha256(bytes),
                            extension,
                            bytes));
            foto.put("clave", clave);
        }

        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("version_contenido", 1);
        contenido.put("ia", sugerencias.llamada(cliente, solicitadoEn, duracion));
        boolean contextoReducido = ejemplar == null || ejemplar.get("especie") == null;
        contenido.put(
                "suficiencia",
                !suficiente
                        ? "insuficiente"
                        : contextoReducido || sintomas == null ? "parcial" : "suficiente");
        List<String> faltantes = new ArrayList<>();
        if (!suficiente) faltantes.add("Fotografías de calidad suficiente");
        if (contextoReducido) faltantes.add("Especie del ejemplar");
        if (sintomas == null) faltantes.add("Descripción de los síntomas");
        contenido.put("faltantes", faltantes);
        contenido.put(
                "datos_utilizados",
                List.of(
                        "Fotografías adjuntas",
                        "Especie y espacio del ejemplar (MOD-02)",
                        "Síntomas descritos",
                        "Catálogo de plagas (MOD-05)"));
        List<String> limitaciones =
                new ArrayList<>(
                        List.of("Son hipótesis: el diagnóstico requiere validación técnica"));
        if (salida.get("limitaciones") instanceof List<?> l)
            l.forEach(x -> limitaciones.add(String.valueOf(x)));
        contenido.put("limitaciones", limitaciones);
        contenido.put(
                "explicacion",
                salida.get("explicacion") instanceof String x
                        ? x
                        : "Hipótesis según las fotografías y la especie.");
        contenido.put("origen", "ia");
        contenido.put("ejemplar", ejemplar);
        contenido.put("atencion", atencion);
        contenido.put("sintomas", sintomas);
        contenido.put("fotografias", fotografias);
        contenido.put("calidad_suficiente", suficiente);
        contenido.put("contexto_reducido", contextoReducido);
        contenido.put("hipotesis", hipotesis);
        contenido.put("validacion", null);
        contenido.put("validada_por", null);
        contenido.put("decision", null);
        return publica(
                sugerencias.crear(
                        TIPO,
                        ejemplarId != null ? "Ejemplar" : "AtencionTercerizada",
                        ejemplarId != null ? ejemplarId : atencionId,
                        contenido,
                        usuario));
    }

    // ------------------------------------------------------------------ revisión

    public Map<String, Object> validar(long id, Map<String, Object> cuerpo, UsuarioSesion usuario) {
        return publica(
                sugerencias.revisar(
                        id,
                        TIPO,
                        usuario.id(),
                        c -> {
                            List<String> claves =
                                    ((List<?>) c.getOrDefault("hipotesis", List.of()))
                                            .stream()
                                                    .map(
                                                            h ->
                                                                    String.valueOf(
                                                                            ((Map<?, ?>) h)
                                                                                    .get("clave")))
                                                    .toList();
                            Map<String, Object> validacion = validacion(cuerpo, claves, true);
                            c.put("validacion", validacion);
                            c.put("validada_por", usuario.nombre());
                            return new SugerenciasIa.Revision(SugerenciasIa.VALIDADA, c, null);
                        }));
    }

    public Map<String, Object> rechazar(long id, String motivo, long usuario) {
        return publica(
                sugerencias.rechazar(
                        id,
                        TIPO,
                        usuario,
                        motivo,
                        c -> Map.of("motivo_rechazo", motivo == null ? "" : motivo)));
    }

    /**
     * Sin IA: el técnico registra su evaluación directamente (resultado distinto de Confirmada).
     */
    public Map<String, Object> manual(Map<String, Object> cuerpo, UsuarioSesion usuario) {
        Long ejemplarId = cuerpo.get("ejemplar_id") instanceof Number n ? n.longValue() : null;
        Long atencionId = cuerpo.get("atencion_id") instanceof Number n ? n.longValue() : null;
        if (ejemplarId == null && atencionId == null) {
            throw new ErrorGestion(400, "datos-invalidos", "Indica el ejemplar o la atención");
        }
        Map<String, Object> validacion = validacion(cuerpo, List.of(), false);
        if (ejemplarId != null && ejemplar(ejemplarId) == null) {
            throw new ErrorGestion(404, "ejemplar-no-encontrado", "No existe el ejemplar indicado");
        }
        if (atencionId != null && atencion(atencionId) == null) {
            throw new ErrorGestion(404, "atencion-no-encontrada", "No existe la atención indicada");
        }
        Map<String, Object> contenido = new LinkedHashMap<>();
        contenido.put("version_contenido", 1);
        contenido.put("ia", sugerencias.llamada(null, sugerencias.ahora(), 0));
        contenido.put("suficiencia", "insuficiente");
        contenido.put("faltantes", List.of());
        contenido.put("datos_utilizados", List.of("Evaluación del técnico"));
        contenido.put("limitaciones", List.of());
        contenido.put("explicacion", "Evaluación registrada manualmente, sin análisis de IA.");
        contenido.put("origen", "manual");
        contenido.put(
                "ejemplar",
                ejemplarId == null ? ejemplarDeAtencion(atencionId) : ejemplar(ejemplarId));
        contenido.put("atencion", atencionId == null ? null : atencion(atencionId));
        contenido.put("sintomas", null);
        contenido.put("fotografias", List.of());
        contenido.put("calidad_suficiente", false);
        contenido.put("contexto_reducido", false);
        contenido.put("hipotesis", List.of());
        contenido.put("validacion", validacion);
        contenido.put("validada_por", usuario.nombre());
        contenido.put("decision", null);
        return publica(
                sugerencias.crearValidada(
                        TIPO,
                        ejemplarId != null ? "Ejemplar" : "AtencionTercerizada",
                        ejemplarId != null ? ejemplarId : atencionId,
                        contenido,
                        usuario.id()));
    }

    public Map<String, Object> obtener(long id) {
        return publica(sugerencias.obtener(id, TIPO));
    }

    public SugerenciasIa.Pagina listar(String estado, String page, Paginacion.EnlacePagina enlace) {
        SugerenciasIa.Pagina p = sugerencias.listar(TIPO, estado, page, enlace);
        return new SugerenciasIa.Pagina(
                p.count(),
                p.next(),
                p.previous(),
                p.results().stream().map(EvidenciasFitosanitariasService::publica).toList());
    }

    /**
     * La fotografía {@code archivoId} (1, 2, …) de la sugerencia, con su tipo. 404 si no existe.
     */
    public Fotografia fotografia(long id, long archivoId) {
        Map<String, Object> s = sugerencias.obtener(id, TIPO);
        Object fotos = ((Map<?, ?>) s.get("contenido")).get("fotografias");
        for (Object o : fotos instanceof List<?> l ? l : List.of()) {
            Map<?, ?> f = (Map<?, ?>) o;
            if (f.get("archivo_id") instanceof Number n
                    && n.longValue() == archivoId
                    && f.get("clave") instanceof String clave) {
                return new Fotografia(
                        String.valueOf(f.get("tipo_mime")), almacenamiento.leer(clave));
            }
        }
        throw new ErrorGestion(404, "fotografia-no-encontrada", "No existe esa fotografía");
    }

    public record Fotografia(String tipoMime, Resource contenido) {}

    // ------------------------------------------------------------------ utilidades

    private static Map<String, Object> validacion(
            Map<String, Object> cuerpo, List<String> claves, boolean conIa) {
        String resultado = cuerpo.get("resultado") instanceof String r ? r : null;
        List<String> permitidos =
                conIa
                        ? List.of("Confirmada", "OtraCategoria", "NoConcluyente")
                        : List.of("OtraCategoria", "NoConcluyente");
        if (resultado == null || !permitidos.contains(resultado)) {
            throw new ErrorGestion(
                    400,
                    "datos-invalidos",
                    "Resultado no válido",
                    Map.of("resultado", List.of("Usa " + String.join(", ", permitidos))));
        }
        String hipotesis = cuerpo.get("hipotesis_clave") instanceof String h ? h : null;
        String categoria = cuerpo.get("categoria") instanceof String c ? c : null;
        if (resultado.equals("Confirmada") && (hipotesis == null || !claves.contains(hipotesis))) {
            throw new ErrorGestion(
                    400,
                    "datos-invalidos",
                    "Elige una hipótesis del análisis",
                    Map.of("hipotesis_clave", List.of("No pertenece al análisis")));
        }
        if (resultado.equals("OtraCategoria")
                && (categoria == null || !CATEGORIAS.contains(categoria))) {
            throw new ErrorGestion(
                    400,
                    "datos-invalidos",
                    "Indica la categoría",
                    Map.of("categoria", List.of("Usa " + String.join(", ", CATEGORIAS))));
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("resultado", resultado);
        v.put("hipotesis_clave", resultado.equals("Confirmada") ? hipotesis : null);
        v.put("categoria", categoria);
        v.put("plaga_id", cuerpo.get("plaga_id") instanceof Number p ? p.longValue() : null);
        v.put("nombre", cuerpo.get("nombre") instanceof String n ? n : null);
        v.put("comentario", cuerpo.get("comentario") instanceof String c ? c : null);
        return v;
    }

    /**
     * Sin la clave de almacenamiento ni el tipo de cada foto: las fotos se piden por su endpoint.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> publica(Map<String, Object> s) {
        if (!(s.get("contenido") instanceof Map<?, ?> c)
                || !(c.get("fotografias") instanceof List<?> fotos)) return s;
        Map<String, Object> contenido = new LinkedHashMap<>((Map<String, Object>) c);
        contenido.put(
                "fotografias",
                fotos.stream()
                        .map(
                                f -> {
                                    Map<String, Object> copia =
                                            new LinkedHashMap<>((Map<String, Object>) f);
                                    copia.remove("clave");
                                    copia.remove("tipo_mime");
                                    return copia;
                                })
                        .toList());
        Map<String, Object> r = new LinkedHashMap<>(s);
        r.put("contenido", contenido);
        return r;
    }

    private Map<?, ?> datos(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return JsonPy.loads(json) instanceof Map<?, ?> m ? m : Map.of();
        } catch (RuntimeException e) {
            throw new ErrorGestion(400, "datos-invalidos", "La parte «datos» no es un JSON válido");
        }
    }

    private Map<String, Object> ejemplar(long id) {
        List<Map<String, Object>> f =
                jdbc.query(
                        """
                        SELECT e.id, e.codigo, COALESCE(s.nombre_comun, s.nombre_cientifico) AS especie, a.nombre AS espacio
                        FROM mod02_ejemplar e LEFT JOIN mod02_especie_vegetal s ON s.id = e.especie_id
                        LEFT JOIN mod02_area_verde a ON a.id = e.area_verde_inventario_id WHERE e.id = :id
                        """,
                        Map.of("id", id),
                        (rs, n) ->
                                Mapas.mapa(
                                        "id",
                                        rs.getLong("id"),
                                        "codigo",
                                        rs.getString("codigo"),
                                        "especie",
                                        rs.getString("especie"),
                                        "espacio",
                                        rs.getString("espacio")));
        return f.isEmpty() ? null : f.get(0);
    }

    /** Las atenciones de MOD-05 no traen la especie: se lee del ejemplar de su alcance. */
    private Map<String, Object> ejemplarDeAtencion(Long atencionId) {
        if (atencionId == null) return null;
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT ejemplar_id FROM mod05_alcanceatencion WHERE atencion_id = :id AND"
                                + " ejemplar_id IS NOT NULL ORDER BY id LIMIT 1",
                        Map.of("id", atencionId),
                        Long.class);
        return ids.isEmpty() ? null : ejemplar(ids.get(0));
    }

    private Map<String, Object> atencion(long id) {
        List<Map<String, Object>> f =
                jdbc.query(
                        "SELECT id, codigo FROM mod05_atenciontercerizada WHERE id = :id",
                        Map.of("id", id),
                        (rs, n) ->
                                Mapas.mapa(
                                        "id", rs.getLong("id"), "codigo", rs.getString("codigo")));
        return f.isEmpty() ? null : f.get(0);
    }

    private Long plagaId(String nombre) {
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM mod05_plaga WHERE lower(btrim(nombre)) = lower(btrim(:n))"
                                + " ORDER BY id LIMIT 1",
                        Map.of("n", nombre),
                        Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** JPG, PNG o WEBP por su firma; {@code null} si es otra cosa. */
    static String tipoImagen(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xff) == 0xff
                && (b[1] & 0xff) == 0xd8
                && (b[2] & 0xff) == 0xff) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
            return "image/png";
        if (b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P') return "image/webp";
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ayuda local para construir mapas con valores nulos. */
    static final class Mapas {
        static Map<String, Object> mapa(Object... pares) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (int i = 0; i < pares.length; i += 2) r.put((String) pares[i], pares[i + 1]);
            return r;
        }
    }
}
