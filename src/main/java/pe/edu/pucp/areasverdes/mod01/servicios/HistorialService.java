package pe.edu.pucp.areasverdes.mod01.servicios;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.areasverdes.mod01.datos.BitacoraRepository;

/**
 * Historial de altas y modificaciones de un registro (RF-T-008, HU-MOD01-010). Se consulta desde el
 * registro correspondiente. Combina la auditoría común con la que MOD-05 ya escribe para sus
 * atenciones, sin migrar esos datos.
 */
@Service
public class HistorialService {

    public record Entrada(
            String accion,
            String usuario,
            OffsetDateTime fechaHora,
            Map<String, Object> valoresAnteriores,
            Map<String, Object> valoresNuevos,
            String motivo) {}

    /** Entidades con historial y el permiso para consultar sus registros. */
    static final Map<String, String> PERMISO_POR_ENTIDAD =
            Map.of(
                    "actividad", "CAM.PARTE.CONSULTAR",
                    "atencion", "SER.ATENCION.REGISTRAR",
                    "incidencia", "PLA.INCIDENCIA.REGISTRAR",
                    "ejemplar", "INV.INVENTARIO.CONSULTAR",
                    "usuario", "CORE.USUARIO.GESTIONAR");

    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() {};

    private final BitacoraRepository bitacora;
    private final Autorizacion autorizacion;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public HistorialService(
            BitacoraRepository bitacora,
            Autorizacion autorizacion,
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper json) {
        this.bitacora = bitacora;
        this.autorizacion = autorizacion;
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Entrada> consultar(String entidad, long registroId, UsuarioSesion usuario) {
        String permiso = PERMISO_POR_ENTIDAD.get(entidad);
        if (permiso == null) {
            throw ErrorCore.invalido("entidad-desconocida", "La entidad no tiene historial");
        }
        autorizacion.exigir(usuario, "CORE.HISTORIAL.CONSULTAR");
        autorizacion.exigir(usuario, permiso);
        List<Entrada> entradas = new ArrayList<>();
        bitacora.cambios(entidad, registroId)
                .forEach(
                        c ->
                                entradas.add(
                                        new Entrada(
                                                c.accion(),
                                                c.usuarioNombre(),
                                                c.fechaHora(),
                                                c.valoresAnteriores(),
                                                c.valoresNuevos(),
                                                c.motivo())));
        if ("atencion".equals(entidad)) {
            entradas.addAll(atencionMod05(registroId));
        }
        boolean montos = autorizacion.puedeVerMontos(usuario);
        return entradas.stream()
                .map(e -> montos ? e : sinMontos(e))
                .sorted(Comparator.comparing(Entrada::fechaHora).reversed())
                .toList();
    }

    private List<Entrada> atencionMod05(long atencionId) {
        return jdbc.query(
                """
                SELECT a.accion, u.nombre usuario, a.fecha_hora, a.valores_anteriores::text antes,
                    a.valores_nuevos::text despues, a.motivo
                FROM mod05_registroauditoria a JOIN mod05_usuario u ON u.id = a.usuario_id
                WHERE a.entidad = 'AtencionTercerizada' AND a.registro_id = :id
                """,
                Map.of("id", atencionId),
                (fila, n) ->
                        new Entrada(
                                fila.getString("accion"),
                                fila.getString("usuario"),
                                fila.getObject("fecha_hora", OffsetDateTime.class),
                                leer(fila.getString("antes")),
                                leer(fila.getString("despues")),
                                vacioANulo(fila.getString("motivo"))));
    }

    /** RNF-008: los importes no salen del servidor para quien no puede verlos. */
    static Entrada sinMontos(Entrada e) {
        return new Entrada(
                e.accion(),
                e.usuario(),
                e.fechaHora(),
                filtrar(e.valoresAnteriores()),
                filtrar(e.valoresNuevos()),
                e.motivo());
    }

    private static Map<String, Object> filtrar(Map<String, Object> valores) {
        var resultado = new LinkedHashMap<String, Object>();
        valores.forEach(
                (clave, valor) -> {
                    String k = clave.toLowerCase(Locale.ROOT);
                    if (!k.contains("monto") && !k.contains("importe") && !k.contains("precio")) {
                        resultado.put(clave, valor);
                    }
                });
        return resultado;
    }

    private Map<String, Object> leer(String texto) {
        try {
            return texto == null ? Map.of() : json.readValue(texto, MAPA);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Auditoría de MOD-05 con JSON inválido", e);
        }
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
