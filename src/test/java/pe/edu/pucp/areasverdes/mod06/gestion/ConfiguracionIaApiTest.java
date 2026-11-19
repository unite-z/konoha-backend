package pe.edu.pucp.areasverdes.mod06.gestion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Proveedor y modelo de IA desde Configuración (AV-395). Las pruebas nunca llaman a la IA. */
class ConfiguracionIaApiTest extends ApiGestion {

    private static final String RUTA = "/api/v1/ia/configuracion/";

    @Test
    void sinElegirMandaElEntornoYSeCambiaDesdeConfiguracion() throws Exception {
        http.perform(get(RUTA).cookie(sesion("jefe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("entorno"))
                .andExpect(jsonPath("$.proveedor").value("gemini"))
                .andExpect(jsonPath("$.proveedores[1].id").value("deepseek"))
                .andExpect(jsonPath("$.proveedores[1].clave_configurada").value(false));

        http.perform(
                        put(RUTA)
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"proveedor\": \"deepseek\", \"modelo\":"
                                                + " \"deepseek-flash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("configuracion"))
                .andExpect(jsonPath("$.proveedor").value("deepseek"))
                .andExpect(jsonPath("$.modelo").value("deepseek-flash"));
    }

    @Test
    void soloSeAceptanProveedoresYModelosDeLaLista() throws Exception {
        http.perform(
                        put(RUTA)
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"proveedor\": \"deepseek\", \"modelo\":"
                                                + " \"deepseek-chat\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.modelo[0]").value("Elige un modelo de la lista"));
        http.perform(
                        put(RUTA)
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"proveedor\": \"openai\", \"modelo\": \"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.proveedor[0]").value("Elige Gemini o DeepSeek"));
    }

    @Test
    void probarSinClaveDiceQueNoEstaConfigurada() throws Exception {
        http.perform(
                        post(RUTA + "probar/")
                                .cookie(sesion("jefe"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"proveedor\": \"deepseek\", \"modelo\":"
                                                + " \"deepseek-flash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.proveedor").value("deepseek"))
                .andExpect(
                        jsonPath("$.detalle")
                                .value(
                                        "El análisis con IA no está configurado; registra los datos"
                                                + " manualmente"));
    }
}
