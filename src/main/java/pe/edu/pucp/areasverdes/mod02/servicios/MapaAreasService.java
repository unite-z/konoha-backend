package pe.edu.pucp.areasverdes.mod02.servicios;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.areasverdes.mod02.api.ErrorCoberturas;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.CambioPoligono;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.CambioUsoFuente;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.EjemplarAfectado;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.ImpactoPoligono;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.PoligonoAsignado;
import pe.edu.pucp.areasverdes.mod02.api.MapaAreasController.UsoFuente;
import pe.edu.pucp.areasverdes.mod05.Usuario;

@Service
public class MapaAreasService {

    private final NamedParameterJdbcTemplate jdbc;

    private final GeometriaCampus geometria;

    public MapaAreasService(NamedParameterJdbcTemplate jdbc, GeometriaCampus geometria) {
        this.jdbc = jdbc;
        this.geometria = geometria;
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
                "SELECT p.id_fuente, p.area_verde_id, a.nombre area_verde_nombre, a.tipo_uso_id"
                    + " area_verde_tipo_uso_id, tu.nombre area_verde_tipo_uso_nombre,"
                    + " a.superficie_m2 superficie_registrada_m2, a.activo area_verde_activa FROM"
                    + " mod02_mapa_poligono p LEFT JOIN mod02_area_verde a ON a.id ="
                    + " p.area_verde_id LEFT JOIN mod02_tipo_uso tu ON tu.id = a.tipo_uso_id ORDER"
                    + " BY p.id_fuente",
                new DataClassRowMapper<>(PoligonoAsignado.class));
    }

    @Transactional
    public PoligonoAsignado asignarPoligono(UUID idFuente, CambioPoligono cambio, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        geometria.bloquearCambios();
        Long destino = cambio.areaVerdeId();
        ImpactoPoligono impacto = impacto(idFuente, destino, usuario);
        if (!impacto.conflictos().isEmpty()) {
            throw new ErrorCoberturas(
                    409,
                    "conflictos-cartograficos",
                    "Hay ejemplares cuyo vínculo quedaría fuera de su área. Revisa el impacto antes"
                            + " de continuar.");
        }
        var confirmacion = cambio.confirmacion();
        var ids = impacto.reasignables().stream().map(EjemplarAfectado::id).toList();
        if (confirmacion != null
                && destino != null
                && Objects.equals(impacto.areaAnteriorId(), destino)) {
            return obtenerPoligono(idFuente);
        }
        if ((confirmacion == null && !ids.isEmpty())
                || (confirmacion != null
                        && (!Objects.equals(confirmacion.areaAnteriorId(), impacto.areaAnteriorId())
                                || confirmacion.ejemplaresIds() == null
                                || !new HashSet<>(confirmacion.ejemplaresIds())
                                        .equals(new HashSet<>(ids))))) {
            throw new ErrorCoberturas(
                    409,
                    "confirmar-impacto-poligono",
                    "Revisa y confirma los ejemplares afectados antes de cambiar el vínculo.");
        }
        guardarVinculo(idFuente, destino, usuario, false);
        if (!ids.isEmpty()) {
            jdbc.update(
                    "UPDATE mod02_ejemplar SET area_verde_inventario_id = :area WHERE id IN (:ids)",
                    Map.of("area", destino, "ids", ids));
        }
        return obtenerPoligono(idFuente);
    }

    public ImpactoPoligono impacto(UUID idFuente, Long destino, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        validarArea(destino);
        var parametros = new MapSqlParameterSource("fuente", idFuente).addValue("destino", destino);
        Long anterior =
                jdbc.query(
                        "SELECT area_verde_id FROM mod02_mapa_poligono WHERE id_fuente = :fuente",
                        parametros,
                        rs -> rs.next() ? rs.getObject(1, Long.class) : null);
        if (Objects.equals(anterior, destino))
            return new ImpactoPoligono(anterior, List.of(), List.of());
        parametros.addValue("anterior", anterior);

        var filas = new DataClassRowMapper<>(EjemplarAfectado.class);
        var afectados =
                anterior == null
                        ? List.<EjemplarAfectado>of()
                        : jdbc.query(
                                """
                                SELECT e.id, e.codigo FROM mod02_ejemplar e
                                JOIN mod02_mapa_geometria g ON g.id_fuente = :fuente
                                WHERE e.area_verde_inventario_id = :anterior AND ST_Covers(g.geometria, e.ubicacion_geografica)
                                    AND NOT EXISTS (
                                        SELECT 1 FROM mod02_mapa_poligono p
                                        JOIN mod02_mapa_geometria otro USING (id_fuente)
                                        WHERE p.area_verde_id = :anterior AND p.id_fuente <> :fuente
                                            AND ST_Covers(otro.geometria, e.ubicacion_geografica))
                                ORDER BY e.codigo
                                """,
                                parametros,
                                filas);
        if (destino == null) {
            return new ImpactoPoligono(
                    anterior,
                    List.of(),
                    geometria.tieneGeometria(anterior, idFuente) ? afectados : List.of());
        }
        var conflictos =
                geometria.tieneGeometria(destino, null)
                        ? List.<EjemplarAfectado>of()
                        : jdbc.query(
                                """
                                SELECT e.id, e.codigo FROM mod02_ejemplar e
                                JOIN mod02_mapa_geometria g ON g.id_fuente = :fuente
                                WHERE e.area_verde_inventario_id = :destino AND e.ubicacion_geografica IS NOT NULL
                                    AND NOT ST_Covers(g.geometria, e.ubicacion_geografica)
                                ORDER BY e.codigo
                                """,
                                parametros,
                                filas);
        return new ImpactoPoligono(anterior, afectados, conflictos);
    }

    @Transactional
    public void vincularPoligonoLibre(UUID idFuente, long areaId, Usuario usuario) {
        AccesoCoberturas.gestionar(usuario);
        geometria.bloquearCambios();
        if (guardarVinculo(idFuente, areaId, usuario, true) == 0) {
            throw new ErrorCoberturas(
                    409, "poligono-ocupado", "El polígono ya está vinculado a otra área");
        }
    }

    private int guardarVinculo(UUID idFuente, Long areaId, Usuario usuario, boolean soloLibre) {
        return jdbc.update(
                "INSERT INTO mod02_mapa_poligono (id_fuente, area_verde_id,"
                        + " fecha_modificacion, usuario_modificacion_id) VALUES"
                        + " (:fuente, :area, now(), :usuario) ON CONFLICT (id_fuente) DO UPDATE SET"
                        + " area_verde_id = EXCLUDED.area_verde_id, fecha_modificacion = now(),"
                        + " usuario_modificacion_id = EXCLUDED.usuario_modificacion_id"
                        + " WHERE NOT :solo_libre OR mod02_mapa_poligono.area_verde_id IS NULL",
                new MapSqlParameterSource()
                        .addValue("fuente", idFuente)
                        .addValue("area", areaId)
                        .addValue("usuario", usuario.id())
                        .addValue("solo_libre", soloLibre));
    }

    private PoligonoAsignado obtenerPoligono(UUID idFuente) {
        return jdbc.queryForObject(
                "SELECT p.id_fuente, p.area_verde_id, a.nombre area_verde_nombre, a.tipo_uso_id"
                    + " area_verde_tipo_uso_id, tu.nombre area_verde_tipo_uso_nombre,"
                    + " a.superficie_m2 superficie_registrada_m2, a.activo area_verde_activa FROM"
                    + " mod02_mapa_poligono p LEFT JOIN mod02_area_verde a ON a.id ="
                    + " p.area_verde_id LEFT JOIN mod02_tipo_uso tu ON tu.id = a.tipo_uso_id WHERE"
                    + " p.id_fuente = :fuente",
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
