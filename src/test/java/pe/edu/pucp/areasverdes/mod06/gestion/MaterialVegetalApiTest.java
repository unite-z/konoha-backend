package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.FuenteMovimientosMaterialNoDisponible;

/**
 * RF-REP-005 · HU-MOD05-029 · CU-MOD05-029 con el adaptador real: en {@code main} no hay
 * movimientos de material, así que responde «Sin datos» con su motivo.
 */
class MaterialVegetalApiTest extends ApiGestion {

    static final String RUTA = "/api/v1/reportes/material-vegetal/";

    ResultActions consultar(String usuario, String consulta) throws Exception {
        return http.perform(get(RUTA + consulta).cookie(sesion(usuario)));
    }

    @Test
    void elJefeVeElReporteSinDatosConSuMotivo() throws Exception {
        consultar("jefe", "?mes=2026-10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sin_datos").value(true))
                .andExpect(
                        jsonPath("$.mensaje_sin_datos")
                                .value(FuenteMovimientosMaterialNoDisponible.MOTIVO))
                .andExpect(jsonPath("$.periodo.desde").value("2026-10-01"))
                .andExpect(jsonPath("$.periodo.hasta").value("2026-10-31"))
                .andExpect(jsonPath("$.filtros_aplicados.mes").value("2026-10"))
                .andExpect(jsonPath("$.filtros_aplicados", hasKey("area_verde_id")))
                .andExpect(jsonPath("$.filtros_aplicados.area_verde_id").value(nullValue()))
                .andExpect(jsonPath("$.indicador.codigo").value("RF-REP-005"))
                .andExpect(jsonPath("$.indicador.descripcion_calculo").isNotEmpty())
                .andExpect(jsonPath("$.consumido").isEmpty())
                .andExpect(jsonPath("$.propagado").isEmpty())
                .andExpect(jsonPath("$.trasladado").isEmpty())
                .andExpect(jsonPath("$.comparacion").isEmpty())
                .andExpect(jsonPath("$.productos_proveedor.sin_datos").value(true))
                .andExpect(jsonPath("$.generado_en").isNotEmpty());
    }

    @Test
    void sinBarraFinalTambienResponde() throws Exception {
        http.perform(get("/api/v1/reportes/material-vegetal?mes=2026-10").cookie(sesion("jefe")))
                .andExpect(status().isOk());
    }

    @Test
    void elPracticantePuedeConsultar() throws Exception {
        consultar("practicante", "?mes=2026-10").andExpect(status().isOk());
    }

    @Test
    void sinMesPideElMes() throws Exception {
        consultar("jefe", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores.mes[0]").value(ErrorGestion.mensajeFalta("mes")));
    }

    @Test
    void unMesInexistenteSeRechaza() throws Exception {
        consultar("jefe", "?mes=2026-13")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.mes[0]").value("Usa el formato AAAA-MM"));
    }

    @Test
    void elEspacioTieneQueSerUnNumero() throws Exception {
        consultar("jefe", "?mes=2026-10&area_verde_id=abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.area_verde_id[0]").value("Usa un número entero"));
    }

    @Test
    void elCapatazNoTienePermiso() throws Exception {
        consultar("capataz", "?mes=2026-10")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("sin-permiso"));
    }

    @Test
    void sinSesionNoSeAutentica() throws Exception {
        http.perform(get(RUTA + "?mes=2026-10")).andExpect(status().isUnauthorized());
    }

    /**
     * MOD-01 deja de aceptar la sesión de una cuenta desactivada: 401 antes de llegar a {@code
     * AccesoGestion}.
     */
    @Test
    void unUsuarioInactivoNoSeAutentica() throws Exception {
        consultar("inactivo", "?mes=2026-10").andExpect(status().isUnauthorized());
    }

    /** HU-MOD06-001, criterio 5, y postcondición del CU: la consulta no modifica nada. */
    @Test
    void laConsultaNoModificaNada() throws Exception {
        long atenciones = contar("mod05_atenciontercerizada");
        long usuarios = contar("mod05_usuario");
        consultar("jefe", "?mes=2026-10").andExpect(status().isOk());
        assertEquals(atenciones, contar("mod05_atenciontercerizada"));
        assertEquals(usuarios, contar("mod05_usuario"));
    }

    /** {@code tabla} es siempre una constante de esta prueba, nunca una entrada externa. */
    private long contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Map.of(), Long.class);
    }
}
