package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-SER-001 (proveedores) y RF-SER-002 (contratos): registro y consulta. Solo el jefe de sección
 * registra.
 */
@Service
public class MaestrosService {
    final Mod05Repository repo;
    final ConsultasService queries;
    final Clock clock;

    public MaestrosService(Mod05Repository repo, ConsultasService queries, Clock clock) {
        this.repo = repo;
        this.queries = queries;
        this.clock = clock;
    }

    static final List<String> VINCULOS =
            List.of("SinVinculo", "Principal", "Subsidiaria", "Vinculada");
    static final String PROVEEDORES =
            """
            SELECT p.id,p.razon_social,p.ruc,p.activo,COALESCE(d.contacto,'') contacto,COALESCE(d.tipo_vinculo,'SinVinculo') tipo_vinculo,
                d.proveedor_relacionado_id,rel.razon_social proveedor_relacionado_nombre,
                (SELECT count(*) FROM mod05_contrato c WHERE c.proveedor_id=p.id) contratos
            FROM mod05_proveedor p LEFT JOIN mod05_proveedor_detalle d ON d.proveedor_id=p.id
            LEFT JOIN mod05_proveedor rel ON rel.id=d.proveedor_relacionado_id
            """;
    static final String CONTRATOS =
            """
            SELECT c.id,c.numero,c.proveedor_id,p.razon_social proveedor_nombre,c.tipo_servicio_id,t.nombre tipo_servicio_nombre,
                c.vigencia_inicio,c.vigencia_fin,d.area_metrado_estimado,d.monto_fijo,d.frecuencia_pactada,COALESCE(d.condiciones,'') condiciones
            FROM mod05_contrato c JOIN mod05_proveedor p ON p.id=c.proveedor_id JOIN mod05_tiposervicio t ON t.id=c.tipo_servicio_id
            LEFT JOIN mod05_contrato_detalle d ON d.contrato_id=c.id
            """;

    // --- Consultas
    // ---------------------------------------------------------------------------------------------

    public ConsultasService.Pagina proveedores(
            Usuario user, Map<String, String> params, String url) {
        user.exigir(false);
        String vinculo = params.getOrDefault("tipo_vinculo", "").strip();
        // El valor sale de una lista cerrada: nunca se concatena texto de la petición.
        String where =
                vinculo.isEmpty()
                        ? ""
                        : " WHERE COALESCE(d.tipo_vinculo,'SinVinculo')='"
                                + ConsultasService.option(vinculo, VINCULOS)
                                + "'";
        return queries.list(
                new ConsultasService.Catalogo(
                        PROVEEDORES + where,
                        "razon_social,id",
                        List.of("razon_social", "ruc"),
                        Map.of("activo", "bool")),
                params,
                url,
                UnaryOperator.identity());
    }

    public Map<String, Object> proveedor(Usuario user, long id) {
        user.exigir(false);
        return repo.one(PROVEEDORES + " WHERE p.id=:id", Map.of("id", id));
    }

    public ConsultasService.Pagina contratos(Usuario user, Map<String, String> params, String url) {
        user.exigir(false);
        return queries.list(
                new ConsultasService.Catalogo(
                        CONTRATOS,
                        "vigencia_fin DESC,id DESC",
                        List.of("numero", "proveedor_nombre", "tipo_servicio_nombre"),
                        Map.of(
                                "proveedor_id",
                                "id",
                                "tipo_servicio_id",
                                "id",
                                "vigente",
                                "vigente")),
                params,
                url,
                this::contratoVisible);
    }

    public Map<String, Object> contrato(Usuario user, long id) {
        user.exigir(false);
        return contratoVisible(repo.one(CONTRATOS + " WHERE c.id=:id", Map.of("id", id)));
    }

    Map<String, Object> contratoVisible(Map<String, Object> fila) {
        if (fila.get("monto_fijo") instanceof BigDecimal monto)
            fila.put("monto_fijo", monto.setScale(2).toPlainString());
        LocalDate hoy = LocalDate.now(clock);
        fila.put(
                "vigente",
                !hoy.isBefore((LocalDate) fila.get("vigencia_inicio"))
                        && !hoy.isAfter((LocalDate) fila.get("vigencia_fin")));
        return fila;
    }

    // --- Registro
    // ----------------------------------------------------------------------------------------------

    Map<String, Object> existente(String tabla, Long id, String nombre) {
        var filas = repo.rows("SELECT * FROM mod05_" + tabla + " WHERE id=:id", Map.of("id", id));
        if (filas.isEmpty()) throw ApiError.invalid(nombre + " no existe");
        return filas.getFirst();
    }

    /**
     * `CT-AAAA-NNN` con el siguiente número libre del año: salta los ya usados (por ejemplo, los
     * contratos precargados).
     */
    String numeroContrato() {
        int anio = LocalDate.now(clock).getYear();
        for (int intento = 0; intento < 1000; intento++) {
            long siguiente =
                    repo.jdbc()
                            .queryForObject(
                                    "SELECT nextval('mod05_contrato_numero_seq')",
                                    Map.of(),
                                    Long.class);
            String numero = String.format("CT-%d-%03d", anio, siguiente);
            if (repo.rows(
                            "SELECT id FROM mod05_contrato WHERE numero=:numero",
                            Map.of("numero", numero))
                    .isEmpty()) return numero;
        }
        throw ApiError.conflict("No se pudo asignar un número de contrato. Inténtalo nuevamente");
    }

    @Transactional
    public Map<String, Object> crearProveedor(EntradasMaestros.Proveedor in, Usuario user) {
        user.exigir(true);
        String tipo = in.tipo_vinculo() == null ? "SinVinculo" : in.tipo_vinculo();
        boolean conRelacionado = tipo.equals("Subsidiaria") || tipo.equals("Vinculada");
        if (conRelacionado && in.proveedor_relacionado_id() == null)
            throw ApiError.invalid("Indica el proveedor relacionado");
        if (!conRelacionado && in.proveedor_relacionado_id() != null)
            throw ApiError.invalid(
                    "Solo una subsidiaria o empresa vinculada indica un proveedor relacionado");
        if (conRelacionado
                && !(boolean)
                        existente(
                                        "proveedor",
                                        in.proveedor_relacionado_id(),
                                        "El proveedor relacionado")
                                .get("activo"))
            throw ApiError.invalid("El proveedor relacionado está inactivo");
        // Serializa registros con el mismo RUC; la restricción UNIQUE de la base es la última
        // defensa.
        repo.jdbc().getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(505101)");
        if (!repo.rows("SELECT id FROM mod05_proveedor WHERE ruc=:ruc", Map.of("ruc", in.ruc()))
                .isEmpty())
            throw new ApiError(
                    409,
                    "ProveedorExistente",
                    "Ya existe un proveedor registrado con el RUC " + in.ruc());
        long id =
                repo.insert(
                        "proveedor",
                        values(
                                "razon_social",
                                in.razon_social().strip(),
                                "ruc",
                                in.ruc(),
                                "activo",
                                true));
        var detalle = new HashMap<String, Object>();
        detalle.put("id", id);
        detalle.put("contacto", AtencionService.text(in.contacto()));
        detalle.put("tipo", tipo);
        detalle.put("relacionado", in.proveedor_relacionado_id());
        repo.jdbc()
                .update(
                        "INSERT INTO"
                            + " mod05_proveedor_detalle(proveedor_id,contacto,tipo_vinculo,proveedor_relacionado_id)"
                            + " VALUES (:id,:contacto,:tipo,:relacionado)",
                        detalle);
        var creado = repo.one(PROVEEDORES + " WHERE p.id=:id", Map.of("id", id));
        repo.audit("Proveedor", id, "Alta", user, Map.of(), creado, "");
        return creado;
    }

    @Transactional
    public Map<String, Object> crearContrato(EntradasMaestros.Contrato in, Usuario user) {
        user.exigir(true);
        if (!(boolean) existente("proveedor", in.proveedor_id(), "El proveedor").get("activo"))
            throw ApiError.invalid("El proveedor está inactivo");
        var servicio = existente("tiposervicio", in.tipo_servicio_id(), "El tipo de servicio");
        if (!(boolean) servicio.get("activo"))
            throw ApiError.invalid("El tipo de servicio está inactivo");
        if (!servicio.get("ejecutor").equals("Tercerizado"))
            throw ApiError.invalid("El contrato debe corresponder a un servicio tercerizado");
        if (in.vigencia_fin().isBefore(in.vigencia_inicio()))
            throw ApiError.invalid("El fin de la vigencia no puede ser anterior al inicio");
        String numero = numeroContrato();
        long id =
                repo.insert(
                        "contrato",
                        values(
                                "numero",
                                numero,
                                "proveedor_id",
                                in.proveedor_id(),
                                "tipo_servicio_id",
                                in.tipo_servicio_id(),
                                "vigencia_inicio",
                                in.vigencia_inicio(),
                                "vigencia_fin",
                                in.vigencia_fin()));
        var detalle = new HashMap<String, Object>();
        detalle.put("id", id);
        detalle.put("area", in.area_metrado_estimado().strip());
        detalle.put("monto", in.monto_fijo());
        detalle.put("frecuencia", in.frecuencia_pactada().strip());
        detalle.put("condiciones", AtencionService.text(in.condiciones()));
        detalle.put("fecha", OffsetDateTime.now(clock));
        detalle.put("usuario", user.id());
        repo.jdbc()
                .update(
                        """
                        INSERT INTO mod05_contrato_detalle(contrato_id,area_metrado_estimado,monto_fijo,frecuencia_pactada,condiciones,fecha_creacion,usuario_creacion_id)
                        VALUES (:id,:area,:monto,:frecuencia,:condiciones,:fecha,:usuario)
                        """,
                        detalle);
        var creado = contratoVisible(repo.one(CONTRATOS + " WHERE c.id=:id", Map.of("id", id)));
        repo.audit("Contrato", id, "Alta", user, Map.of(), creado, "");
        return creado;
    }
}
