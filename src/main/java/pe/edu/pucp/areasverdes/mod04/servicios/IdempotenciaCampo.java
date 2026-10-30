package pe.edu.pucp.areasverdes.mod04.servicios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod04.datos.Mod04Repository;
import pe.edu.pucp.areasverdes.mod05.ApiError;
import pe.edu.pucp.areasverdes.mod05.Usuario;

/** Evita duplicar altas de campo cuando se reenvía la misma operación. */
@Service
public class IdempotenciaCampo {
    private final Mod04Repository repo;
    private final OperacionCampoService acceso;
    private final ObjectMapper json;

    public IdempotenciaCampo(
            Mod04Repository repo, OperacionCampoService acceso, ObjectMapper json) {
        this.repo = repo;
        this.acceso = acceso;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> ejecutar(
            UUID clave,
            Usuario usuario,
            String operacion,
            Object cuerpo,
            Supplier<Map<String, Object>> alta) {
        acceso.exigirLectura(usuario);
        if (clave == null) return alta.get();
        try {
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (operacion
                                                                    + ":"
                                                                    + json.writeValueAsString(
                                                                            cuerpo))
                                                            .getBytes(StandardCharsets.UTF_8)));
            var parametros =
                    Map.<String, Object>of("usuario", usuario.id(), "clave", clave, "hash", hash);
            repo.jdbc()
                    .queryForObject(
                            "SELECT pg_advisory_xact_lock(hashtextextended(cast(:usuario AS"
                                    + " text)||':'||cast(:clave AS text),0)) IS NULL",
                            parametros,
                            Boolean.class);
            repo.jdbc()
                    .update(
                            "DELETE FROM mod04_idempotencia WHERE usuario_id=:usuario AND"
                                    + " clave=:clave AND caduca<=now()",
                            parametros);
            var anteriores =
                    repo.rows(
                            "SELECT hash,respuesta::text respuesta FROM mod04_idempotencia WHERE"
                                    + " usuario_id=:usuario AND clave=:clave",
                            parametros);
            if (!anteriores.isEmpty()) {
                if (!hash.equals(anteriores.getFirst().get("hash")))
                    throw new ApiError(
                            409,
                            "idempotency-key-reutilizada",
                            "La clave de operación ya se utilizó con otros datos");
                return json.readValue(
                        (String) anteriores.getFirst().get("respuesta"), new TypeReference<>() {});
            }
            var respuesta = alta.get();
            var guardar = new java.util.HashMap<>(parametros);
            guardar.put("respuesta", json.writeValueAsString(respuesta));
            repo.jdbc()
                    .update(
                            "INSERT INTO mod04_idempotencia(usuario_id,clave,hash,respuesta) VALUES"
                                    + " (:usuario,:clave,:hash,cast(:respuesta AS jsonb))",
                            guardar);
            return respuesta;
        } catch (ApiError e) {
            throw e;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo registrar la clave de operación", e);
        }
    }
}
