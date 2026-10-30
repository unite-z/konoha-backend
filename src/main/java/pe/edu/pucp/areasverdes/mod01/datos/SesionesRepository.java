package pe.edu.pucp.areasverdes.mod01.datos;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Sesiones revocables ({@code core_sesion}). Solo se guarda el SHA-256 del token. */
@Repository
public class SesionesRepository {

    public record Sesion(
            long id,
            long usuarioId,
            String canal,
            boolean persistente,
            OffsetDateTime ultimaActividad,
            OffsetDateTime expira,
            OffsetDateTime revocada) {}

    private final NamedParameterJdbcTemplate jdbc;

    public SesionesRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long crear(
            String tokenHash,
            long usuarioId,
            String canal,
            boolean persistente,
            OffsetDateTime ahora,
            OffsetDateTime expira) {
        var p = new HashMap<String, Object>();
        p.put("hash", tokenHash);
        p.put("usuario", usuarioId);
        p.put("canal", canal);
        p.put("persistente", persistente);
        p.put("ahora", ahora);
        p.put("expira", expira);
        return jdbc.queryForObject(
                """
                INSERT INTO core_sesion (token_hash, usuario_id, canal, persistente, creada,
                    ultima_actividad, expira)
                VALUES (:hash, :usuario, :canal, :persistente, :ahora, :ahora, :expira)
                RETURNING id
                """,
                p,
                Long.class);
    }

    public Optional<Sesion> porToken(String tokenHash) {
        return jdbc
                .query(
                        """
                        SELECT id, usuario_id, canal, persistente, ultima_actividad, expira,
                            revocada
                        FROM core_sesion WHERE token_hash = :hash
                        """,
                        Map.of("hash", tokenHash),
                        new DataClassRowMapper<>(Sesion.class))
                .stream()
                .findFirst();
    }

    public void tocar(long id, OffsetDateTime ahora) {
        jdbc.update(
                "UPDATE core_sesion SET ultima_actividad = :ahora WHERE id = :id",
                Map.of("id", id, "ahora", ahora));
    }

    public void revocar(long id, OffsetDateTime ahora, String motivo) {
        jdbc.update(
                """
                UPDATE core_sesion SET revocada = :ahora, motivo_revocacion = :motivo
                WHERE id = :id AND revocada IS NULL
                """,
                Map.of("id", id, "ahora", ahora, "motivo", motivo));
    }

    /** Revoca todas las sesiones del usuario salvo {@code excepto} (puede ser {@code null}). */
    public int revocarTodas(long usuarioId, Long excepto, OffsetDateTime ahora, String motivo) {
        var p = new HashMap<String, Object>();
        p.put("usuario", usuarioId);
        p.put("excepto", excepto);
        p.put("ahora", ahora);
        p.put("motivo", motivo);
        return jdbc.update(
                """
                UPDATE core_sesion SET revocada = :ahora, motivo_revocacion = :motivo
                WHERE usuario_id = :usuario AND revocada IS NULL
                    AND (CAST(:excepto AS bigint) IS NULL OR id <> :excepto)
                """,
                p);
    }
}
