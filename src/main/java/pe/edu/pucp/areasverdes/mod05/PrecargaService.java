package pe.edu.pucp.areasverdes.mod05;

import static pe.edu.pucp.areasverdes.mod05.AtencionService.values;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrecargaService {
    final Mod05Repository repo;
    final ObjectMapper json;
    final PasswordEncoder passwords;

    public PrecargaService(Mod05Repository repo, ObjectMapper json, PasswordEncoder passwords) {
        this.repo = repo;
        this.json = json;
        this.passwords = passwords;
    }

    record Fuente(String table, String file, List<String> fields) {}

    static final List<Fuente> FUENTES =
            List.of(
                    new Fuente(
                            "proveedor",
                            "mod05/Proveedor.json",
                            List.of("razon_social", "ruc", "activo")),
                    new Fuente(
                            "tiposervicio",
                            "mod03/TipoServicio.json",
                            List.of("nombre", "ejecutor", "es_riesgo", "activo")),
                    new Fuente(
                            "unidadmedida",
                            "mod04/UnidadMedida.json",
                            List.of("nombre", "simbolo")),
                    new Fuente("areaverde", "mod02/AreaVerde.json", List.of("nombre", "activo")),
                    new Fuente(
                            "ejemplar", "mod02/Ejemplar.json", List.of("codigo", "area_verde_id")),
                    new Fuente(
                            "contrato",
                            "mod05/Contrato.json",
                            List.of(
                                    "numero",
                                    "proveedor_id",
                                    "tipo_servicio_id",
                                    "vigencia_inicio",
                                    "vigencia_fin")));

    @Transactional
    public void cargar(Path folder, String password) {
        if (password == null || password.length() < 12)
            throw new IllegalArgumentException(
                    "MOD05_DEMO_PASSWORD debe tener al menos 12 caracteres");
        // Serializa precargas concurrentes; nunca sobrescribe registros operativos.
        repo.jdbc().getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(505003)");
        try {
            for (var f : FUENTES) {
                List<Map<String, Object>> rows =
                        json.readValue(folder.resolve(f.file()).toFile(), new TypeReference<>() {});
                for (var row : rows) {
                    long id = ((Number) row.get("id")).longValue();
                    var data = values("id", id);
                    for (var field : f.fields()) {
                        if (!row.containsKey(field))
                            throw new IllegalArgumentException(
                                    "Campo faltante " + field + " en " + f.file());
                        Object value = row.get(field);
                        if (field.startsWith("vigencia_")) value = LocalDate.parse((String) value);
                        data.put(field, value);
                    }
                    var existing =
                            repo.rows(
                                    "SELECT * FROM mod05_" + f.table() + " WHERE id=:id",
                                    Map.of("id", id));
                    if (existing.isEmpty()) repo.insert(f.table(), data);
                    else
                        for (var field : f.fields()) {
                            if (!Objects.equals(
                                    Objects.toString(data.get(field), null),
                                    Objects.toString(existing.getFirst().get(field), null)))
                                throw new IllegalArgumentException(
                                        "Conflicto de precarga en "
                                                + f.table()
                                                + " id="
                                                + id
                                                + "; no se sobrescribe");
                        }
                }
                String tablaSecuencia =
                        f.table().equals("ejemplar") ? "mod02_ejemplar" : "mod05_" + f.table();
                repo.jdbc()
                        .getJdbcTemplate()
                        .execute(
                                "SELECT setval(pg_get_serial_sequence('"
                                        + tablaSecuencia
                                        + "','id'),coalesce((SELECT max(id) FROM "
                                        + tablaSecuencia
                                        + "),1),EXISTS(SELECT 1 FROM "
                                        + tablaSecuencia
                                        + "))");
                if (f.table().equals("ejemplar")) {
                    repo.jdbc()
                            .getJdbcTemplate()
                            .execute(
                                    "SELECT setval('mod02_ejemplar_codigo_seq',"
                                            + " coalesce(max(substring(codigo FROM"
                                            + " '^EJ-([0-9]+)$')::bigint), 0) + 1, false)"
                                            + " FROM mod02_ejemplar");
                }
            }
            repo.jdbc()
                    .update(
                            "INSERT INTO mod05_unidadinstitucional(nombre,provisional,activo)"
                                    + " VALUES (:n,false,true) ON CONFLICT(nombre) DO NOTHING",
                            Map.of("n", "Sección de Áreas Verdes y Medio Ambiente"));
            String[] users = {"mod05_jefe", "mod05_ingeniero", "mod05_practicante"};
            for (int i = 0; i < users.length; i++) {
                if (repo.usuario(users[i]).isPresent()) continue;
                long id =
                        repo.insert(
                                "usuario",
                                values(
                                        "usuario",
                                        users[i],
                                        "nombre",
                                        users[i],
                                        "password_hash",
                                        passwords.encode(password),
                                        "activo",
                                        true));
                repo.jdbc()
                        .update(
                                "INSERT INTO mod05_usuario_rol(usuario_id,rol) VALUES (:id,:rol)",
                                Map.of("id", id, "rol", Usuario.ROLES.get(i)));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Precarga inválida; se revierte toda la operación", e);
        }
    }
}
