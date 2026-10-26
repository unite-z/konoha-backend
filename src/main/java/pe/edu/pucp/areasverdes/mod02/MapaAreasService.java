package pe.edu.pucp.areasverdes.mod02;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.MapaAreasController.CambioPoligono;
import pe.edu.pucp.areasverdes.mod02.MapaAreasController.CambioUsoFuente;
import pe.edu.pucp.areasverdes.mod02.MapaAreasController.PoligonoAsignado;
import pe.edu.pucp.areasverdes.mod02.MapaAreasController.UsoFuente;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class MapaAreasService {

    private final NamedParameterJdbcTemplate jdbc;

    public MapaAreasService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UsoFuente> usosFuente() {
        return jdbc.query(
                "SELECT f.uso_fuente, f.tipo_uso_id, u.nombre tipo_uso_nombre"
                        + " FROM mod02_mapa_uso_fuente f LEFT JOIN mod02_tipo_uso u"
                        + " ON u.id = f.tipo_uso_id ORDER BY lower(f.uso_fuente)",
                new DataClassRowMapper<>(UsoFuente.class));
    }

    @Transactional
    public UsoFuente cambiarUsoFuente(CambioUsoFuente cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        String usoFuente = cambio.usoFuente().trim();
        validarTipoUso(cambio.tipoUsoId());
        jdbc.update(
                "INSERT INTO mod02_mapa_uso_fuente (uso_fuente, tipo_uso_id,"
                        + " fecha_modificacion, usuario_modificacion_id) VALUES"
                        + " (:uso, :tipo, now(), :usuario) ON CONFLICT (uso_fuente) DO UPDATE SET"
                        + " tipo_uso_id = EXCLUDED.tipo_uso_id, fecha_modificacion = now(),"
                        + " usuario_modificacion_id = EXCLUDED.usuario_modificacion_id",
                new MapSqlParameterSource()
                        .addValue("uso", usoFuente)
                        .addValue("tipo", cambio.tipoUsoId())
                        .addValue("usuario", usuario.id()));
        return jdbc.queryForObject(
                "SELECT f.uso_fuente, f.tipo_uso_id, u.nombre tipo_uso_nombre"
                        + " FROM mod02_mapa_uso_fuente f LEFT JOIN mod02_tipo_uso u"
                        + " ON u.id = f.tipo_uso_id WHERE f.uso_fuente = :uso",
                Map.of("uso", usoFuente),
                new DataClassRowMapper<>(UsoFuente.class));
    }

    public List<PoligonoAsignado> poligonos() {
        return jdbc.query(
                "SELECT p.id_fuente, p.area_verde_id, a.nombre area_verde_nombre"
                        + " FROM mod02_mapa_poligono p LEFT JOIN mod02_area_verde a"
                        + " ON a.id = p.area_verde_id ORDER BY p.id_fuente",
                new DataClassRowMapper<>(PoligonoAsignado.class));
    }

    @Transactional
    public PoligonoAsignado asignarPoligono(UUID idFuente, CambioPoligono cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        validarArea(cambio.areaVerdeId());
        jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id,"
                        + " fecha_modificacion, usuario_modificacion_id) VALUES"
                        + " (:fuente, :area, now(), :usuario) ON CONFLICT (id_fuente) DO UPDATE SET"
                        + " area_verde_id = EXCLUDED.area_verde_id, fecha_modificacion = now(),"
                        + " usuario_modificacion_id = EXCLUDED.usuario_modificacion_id",
                new MapSqlParameterSource()
                        .addValue("fuente", idFuente)
                        .addValue("area", cambio.areaVerdeId())
                        .addValue("usuario", usuario.id()));
        return jdbc.queryForObject(
                "SELECT p.id_fuente, p.area_verde_id, a.nombre area_verde_nombre"
                        + " FROM mod02_mapa_poligono p LEFT JOIN mod02_area_verde a"
                        + " ON a.id = p.area_verde_id WHERE p.id_fuente = :fuente",
                Map.of("fuente", idFuente),
                new DataClassRowMapper<>(PoligonoAsignado.class));
    }

    private void validarTipoUso(Long id) {
        if (id == null) return;
        Boolean activo =
                jdbc.query(
                        "SELECT activo FROM mod02_tipo_uso WHERE id = :id",
                        Map.of("id", id),
                        rs -> rs.next() ? rs.getBoolean("activo") : null);
        if (!Boolean.TRUE.equals(activo)) {
            throw new ErrorCoberturas(
                    404, "tipo-uso-no-encontrado", "El tipo de uso no existe o está inactivo");
        }
    }

    private void validarArea(Long id) {
        if (id == null) return;
        Boolean activa =
                jdbc.query(
                        "SELECT activo FROM mod02_area_verde WHERE id = :id",
                        Map.of("id", id),
                        rs -> rs.next() ? rs.getBoolean("activo") : null);
        if (!Boolean.TRUE.equals(activa)) {
            throw new ErrorCoberturas(
                    404, "area-no-encontrada", "El área no existe o está inactiva");
        }
    }
}
