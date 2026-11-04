package pe.edu.pucp.areasverdes.mod01.importacion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;

/**
 * Catastro forestal: altas y actualizaciones periódicas de {@code mod02_ejemplar}. La clave es el
 * código de catastro, único en la base, así que repetir la carga no duplica registros. Las
 * cabeceras coinciden con las del listado que exporta RF-T-011.
 */
@Component
class ImportadorCatastro implements Importador {

    private static final String CODIGO = "código de catastro";
    private static final String REFERENCIA = "referencia de catastro";
    private static final String ESPECIE = "especie";
    private static final String VEGETACION = "tipo de vegetación";
    private static final String AREA = "área verde";
    private static final String UBICACION = "ubicación";
    private static final String CANTIDAD = "cantidad";

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    ImportadorCatastro(NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Override
    public String tipo() {
        return "catastro";
    }

    @Override
    public String permiso() {
        return "INV.INVENTARIO.REGISTRAR";
    }

    @Override
    public List<String> columnas() {
        return List.of(CODIGO, ESPECIE);
    }

    /** Valores que se escribirían, una vez validada la fila. */
    private record Valores(
            String referencia,
            long especieId,
            String vegetacion,
            Long areaId,
            String ubicacion,
            int cantidad) {}

    private record Existente(
            long id,
            String referencia,
            Long especieId,
            String vegetacion,
            Long areaId,
            String ubicacion,
            int cantidad) {}

    @Override
    public Clasificacion clasificar(Map<String, String> fila, Set<String> vistos) {
        String codigo = fila.getOrDefault(CODIGO, "");
        if (codigo.isEmpty()) {
            return Clasificacion.error(null, "Falta el código de catastro");
        }
        if (!vistos.add(codigo)) {
            return new Clasificacion(codigo, REVISAR, "Código repetido en el archivo", null);
        }
        String especie = fila.getOrDefault(ESPECIE, "");
        if (especie.isEmpty()) {
            return Clasificacion.error(codigo, "Falta especie");
        }
        if (codigo.length() > 80
                || texto(fila, REFERENCIA, 80) == null && !vacio(fila, REFERENCIA)
                || texto(fila, VEGETACION, 80) == null && !vacio(fila, VEGETACION)
                || texto(fila, UBICACION, 200) == null && !vacio(fila, UBICACION)) {
            return Clasificacion.error(codigo, "Un valor supera la longitud permitida");
        }
        Long especieId = idPorNombre(ESPECIE_POR_NOMBRE, especie);
        if (especieId == null) {
            return Clasificacion.error(codigo, "Especie no registrada: " + especie);
        }
        Long areaId = null;
        if (!vacio(fila, AREA)) {
            areaId = idPorNombre(AREA_POR_NOMBRE, fila.get(AREA));
            if (areaId == null) {
                return Clasificacion.error(codigo, "Área verde no registrada: " + fila.get(AREA));
            }
        }
        Integer cantidad = cantidad(fila.getOrDefault(CANTIDAD, ""));
        if (cantidad == null) {
            return Clasificacion.error(codigo, "La cantidad debe ser un entero positivo");
        }
        Valores valores =
                new Valores(
                        texto(fila, REFERENCIA, 80),
                        especieId,
                        texto(fila, VEGETACION, 80),
                        areaId,
                        texto(fila, UBICACION, 200),
                        cantidad);
        Existente existente = existente(codigo);
        if (valores.referencia() != null && referenciaAjena(valores.referencia(), codigo)) {
            return new Clasificacion(codigo, REVISAR, "Posible duplicado: referencia en uso", null);
        }
        if (existente == null) {
            return new Clasificacion(codigo, ALTA, "", null);
        }
        List<String> cambios = cambios(existente, valores);
        return cambios.isEmpty()
                ? new Clasificacion(codigo, SIN_CAMBIOS, "", existente.id())
                : new Clasificacion(
                        codigo,
                        ACTUALIZACION,
                        "Cambia: " + String.join(", ", cambios),
                        existente.id());
    }

    @Override
    public void aplicar(Map<String, String> fila, Clasificacion clasificacion, long usuarioId) {
        Map<String, Object> p = new HashMap<>();
        p.put("codigo", clasificacion.codigo());
        p.put("referencia", texto(fila, REFERENCIA, 80));
        p.put("especie", idPorNombre(ESPECIE_POR_NOMBRE, fila.get(ESPECIE)));
        p.put("vegetacion", texto(fila, VEGETACION, 80));
        p.put("area", vacio(fila, AREA) ? null : idPorNombre(AREA_POR_NOMBRE, fila.get(AREA)));
        p.put("ubicacion", texto(fila, UBICACION, 200));
        p.put("cantidad", cantidad(fila.getOrDefault(CANTIDAD, "")));
        if (ALTA.equals(clasificacion.resultado())) {
            Long id =
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod02_ejemplar (codigo_catastro, referencia_catastro,
                                especie_id, tipo_vegetacion, area_verde_id, ubicacion, cantidad)
                            VALUES (:codigo, :referencia, :especie, :vegetacion, :area, :ubicacion,
                                :cantidad)
                            RETURNING id
                            """,
                            p,
                            Long.class);
            auditoria.registrarAlta("ejemplar", id, sinNulos(p), usuarioId);
        } else {
            p.put("id", clasificacion.registroId());
            jdbc.update(
                    """
                    UPDATE mod02_ejemplar SET referencia_catastro = :referencia,
                        especie_id = :especie, tipo_vegetacion = :vegetacion, area_verde_id = :area,
                        ubicacion = :ubicacion, cantidad = :cantidad
                    WHERE id = :id
                    """,
                    p);
            auditoria.registrarCambio(
                    "ejemplar",
                    clasificacion.registroId(),
                    "Modificacion",
                    Map.of(),
                    sinNulos(p),
                    clasificacion.detalle(),
                    usuarioId);
        }
    }

    private static final String ESPECIE_POR_NOMBRE =
            """
            SELECT id FROM mod02_especie_vegetal
            WHERE activo AND (lower(nombre_comun) = lower(:n) OR lower(nombre_cientifico) = lower(:n))
            ORDER BY id LIMIT 1
            """;

    private static final String AREA_POR_NOMBRE =
            "SELECT id FROM mod05_areaverde WHERE activo AND lower(nombre) = lower(:n)"
                    + " ORDER BY id LIMIT 1";

    private Long idPorNombre(String consulta, String nombre) {
        return jdbc.queryForList(consulta, Map.of("n", nombre), Long.class).stream()
                .findFirst()
                .orElse(null);
    }

    private Existente existente(String codigo) {
        return jdbc
                .query(
                        """
                        SELECT id, referencia_catastro, especie_id, tipo_vegetacion, area_verde_id,
                            ubicacion, cantidad
                        FROM mod02_ejemplar WHERE codigo_catastro = :c
                        """,
                        Map.of("c", codigo),
                        (f, n) ->
                                new Existente(
                                        f.getLong("id"),
                                        f.getString("referencia_catastro"),
                                        f.getObject("especie_id", Long.class),
                                        f.getString("tipo_vegetacion"),
                                        f.getObject("area_verde_id", Long.class),
                                        f.getString("ubicacion"),
                                        f.getInt("cantidad")))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private boolean referenciaAjena(String referencia, String codigo) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS (SELECT 1 FROM mod02_ejemplar WHERE referencia_catastro = :r
                            AND codigo_catastro IS DISTINCT FROM :c)
                        """,
                        Map.of("r", referencia, "c", codigo),
                        Boolean.class));
    }

    private static List<String> cambios(Existente antes, Valores nuevos) {
        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(antes.referencia(), nuevos.referencia())) cambios.add("referencia");
        if (!Objects.equals(antes.especieId(), nuevos.especieId())) cambios.add("especie");
        if (!Objects.equals(antes.vegetacion(), nuevos.vegetacion())) cambios.add("vegetación");
        if (!Objects.equals(antes.areaId(), nuevos.areaId())) cambios.add("área verde");
        if (!Objects.equals(antes.ubicacion(), nuevos.ubicacion())) cambios.add("ubicación");
        if (antes.cantidad() != nuevos.cantidad()) cambios.add("cantidad");
        return cambios;
    }

    private static boolean vacio(Map<String, String> fila, String columna) {
        return fila.getOrDefault(columna, "").isEmpty();
    }

    /** El valor, o {@code null} si está vacío o supera la longitud máxima. */
    private static String texto(Map<String, String> fila, String columna, int maximo) {
        String valor = fila.getOrDefault(columna, "");
        return valor.isEmpty() || valor.length() > maximo ? null : valor;
    }

    /** Sin valor es 1; un valor no entero o no positivo es {@code null}. */
    private static Integer cantidad(String valor) {
        if (valor.isEmpty()) {
            return 1;
        }
        try {
            int cantidad = new java.math.BigDecimal(valor).intValueExact();
            return cantidad > 0 ? cantidad : null;
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Object> sinNulos(Map<String, Object> valores) {
        Map<String, Object> limpio = new HashMap<>(valores);
        limpio.values().removeIf(Objects::isNull);
        return limpio;
    }
}
