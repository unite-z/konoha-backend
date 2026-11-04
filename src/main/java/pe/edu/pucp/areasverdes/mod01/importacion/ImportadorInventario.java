package pe.edu.pucp.areasverdes.mod01.importacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import pe.edu.pucp.areasverdes.mod01.servicios.RegistradorAuditoria;

/**
 * Inventario de áreas verdes ({@code mod02_area_verde}). La tabla no tiene una clave natural única,
 * así que un área se reconoce por nombre y lugar; si esa pareja ya está repetida en la base, la
 * fila queda por revisar en lugar de elegir una al azar.
 */
@Component
class ImportadorInventario implements Importador {

    private static final String NOMBRE = "nombre";
    private static final String LUGAR = "lugar";
    private static final String UBICACION = "ubicación";
    private static final String SUPERFICIE = "superficie (m²)";
    private static final String COBERTURA = "cobertura";
    private static final String TIPO_USO = "tipo de uso";

    private record Existente(
            long id, String ubicacion, BigDecimal superficie, Long cobertura, Long tipoUso) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final RegistradorAuditoria auditoria;

    ImportadorInventario(NamedParameterJdbcTemplate jdbc, RegistradorAuditoria auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Override
    public String tipo() {
        return "inventario";
    }

    @Override
    public String permiso() {
        return "INV.INVENTARIO.REGISTRAR";
    }

    @Override
    public List<String> columnas() {
        return List.of(NOMBRE, LUGAR, UBICACION, SUPERFICIE);
    }

    @Override
    public Clasificacion clasificar(Map<String, String> fila, Set<String> vistos) {
        String nombre = fila.getOrDefault(NOMBRE, "");
        String lugar = fila.getOrDefault(LUGAR, "");
        String ubicacion = fila.getOrDefault(UBICACION, "");
        if (nombre.isEmpty() || lugar.isEmpty() || ubicacion.isEmpty()) {
            return Clasificacion.error(nombre, "Faltan nombre, lugar o ubicación");
        }
        if (nombre.length() > 150 || lugar.length() > 150 || ubicacion.length() > 250) {
            return Clasificacion.error(nombre, "Un valor supera la longitud permitida");
        }
        String codigo = nombre + " · " + lugar;
        if (!vistos.add(codigo.toLowerCase(Locale.ROOT))) {
            return new Clasificacion(codigo, REVISAR, "Área repetida en el archivo", null);
        }
        BigDecimal superficie = superficie(fila.getOrDefault(SUPERFICIE, ""));
        if (superficie == null) {
            return Clasificacion.error(codigo, "La superficie debe ser un número mayor que cero");
        }
        String cobertura = fila.getOrDefault(COBERTURA, "");
        Long coberturaId = catalogo("mod02_cobertura_vegetal", cobertura);
        if (!cobertura.isEmpty() && coberturaId == null) {
            return Clasificacion.error(codigo, "Cobertura no registrada: " + cobertura);
        }
        String tipoUso = fila.getOrDefault(TIPO_USO, "");
        Long tipoUsoId = catalogo("mod02_tipo_uso", tipoUso);
        if (!tipoUso.isEmpty() && tipoUsoId == null) {
            return Clasificacion.error(codigo, "Tipo de uso no registrado: " + tipoUso);
        }
        List<Existente> existentes = existentes(nombre, lugar);
        if (existentes.size() > 1) {
            return new Clasificacion(codigo, REVISAR, "Posible duplicado en el inventario", null);
        }
        if (existentes.isEmpty()) {
            return new Clasificacion(codigo, ALTA, "", null);
        }
        Existente antes = existentes.get(0);
        List<String> cambios = new ArrayList<>();
        if (!ubicacion.equals(antes.ubicacion())) cambios.add("ubicación");
        if (superficie.compareTo(antes.superficie()) != 0) cambios.add("superficie");
        if (!Objects.equals(coberturaId, antes.cobertura())) cambios.add("cobertura");
        if (!Objects.equals(tipoUsoId, antes.tipoUso())) cambios.add("tipo de uso");
        return cambios.isEmpty()
                ? new Clasificacion(codigo, SIN_CAMBIOS, "", antes.id())
                : new Clasificacion(
                        codigo, ACTUALIZACION, "Cambia: " + String.join(", ", cambios), antes.id());
    }

    @Override
    public void aplicar(Map<String, String> fila, Clasificacion clasificacion, long usuarioId) {
        Map<String, Object> p = new HashMap<>();
        p.put("nombre", fila.get(NOMBRE));
        p.put("lugar", fila.get(LUGAR));
        p.put("ubicacion", fila.get(UBICACION));
        p.put("superficie", superficie(fila.get(SUPERFICIE)));
        p.put("cobertura", catalogo("mod02_cobertura_vegetal", fila.getOrDefault(COBERTURA, "")));
        p.put("tipoUso", catalogo("mod02_tipo_uso", fila.getOrDefault(TIPO_USO, "")));
        p.put("usuario", usuarioId);
        Map<String, Object> auditados = new HashMap<>(p);
        auditados.values().removeIf(Objects::isNull);
        if (ALTA.equals(clasificacion.resultado())) {
            Long id =
                    jdbc.queryForObject(
                            """
                            INSERT INTO mod02_area_verde (nombre, lugar, ubicacion, superficie_m2,
                                tipo_cobertura_id, tipo_uso_id, usuario_creacion_id,
                                usuario_modificacion_id)
                            VALUES (:nombre, :lugar, :ubicacion, :superficie, :cobertura, :tipoUso,
                                :usuario, :usuario)
                            RETURNING id
                            """,
                            p,
                            Long.class);
            auditoria.registrarAlta("mod02_area_verde", id, auditados, usuarioId);
        } else {
            p.put("id", clasificacion.registroId());
            jdbc.update(
                    """
                    UPDATE mod02_area_verde SET ubicacion = :ubicacion, superficie_m2 = :superficie,
                        tipo_cobertura_id = :cobertura, tipo_uso_id = :tipoUso,
                        usuario_modificacion_id = :usuario, fecha_modificacion = now()
                    WHERE id = :id
                    """,
                    p);
            auditoria.registrarCambio(
                    "mod02_area_verde",
                    clasificacion.registroId(),
                    "Modificacion",
                    Map.of(),
                    auditados,
                    clasificacion.detalle(),
                    usuarioId);
        }
    }

    /**
     * {@code tabla} solo recibe las dos constantes de esta clase, nunca una entrada del usuario.
     */
    private Long catalogo(String tabla, String nombre) {
        if (nombre.isEmpty()) {
            return null;
        }
        return jdbc
                .queryForList(
                        "SELECT id FROM " + tabla + " WHERE activo AND lower(nombre) = lower(:n)",
                        Map.of("n", nombre),
                        Long.class)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<Existente> existentes(String nombre, String lugar) {
        return jdbc.query(
                """
                SELECT id, ubicacion, superficie_m2, tipo_cobertura_id, tipo_uso_id
                FROM mod02_area_verde
                WHERE activo AND lower(nombre) = lower(:nombre) AND lower(lugar) = lower(:lugar)
                """,
                Map.of("nombre", nombre, "lugar", lugar),
                (f, n) ->
                        new Existente(
                                f.getLong("id"),
                                f.getString("ubicacion"),
                                f.getBigDecimal("superficie_m2"),
                                f.getObject("tipo_cobertura_id", Long.class),
                                f.getObject("tipo_uso_id", Long.class)));
    }

    /**
     * Hasta 12 dígitos con 2 decimales, como la columna; si no cabe o no es positiva, {@code null}.
     */
    private static BigDecimal superficie(String valor) {
        try {
            BigDecimal superficie = new BigDecimal(valor);
            return superficie.signum() > 0
                            && superficie.scale() <= 2
                            && superficie.precision() - superficie.scale() <= 10
                    ? superficie
                    : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
