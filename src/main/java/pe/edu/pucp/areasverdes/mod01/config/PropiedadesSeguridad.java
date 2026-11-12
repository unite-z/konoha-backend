package pe.edu.pucp.areasverdes.mod01.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parámetros de seguridad de MOD-01 ({@code core.seguridad.*}). Son supuestos del equipo (ACC-S4 a
 * ACC-S6) pendientes de confirmar con la contraparte: por eso se configuran y no van en el código
 * (RNF-011).
 *
 * @param intentosMaximos fallos seguidos que bloquean la cuenta (HU-MOD01-001)
 * @param minutosBloqueo duración del bloqueo
 * @param minutosInactividad cierre por inactividad en oficina (CU-MOD01-002 5b)
 * @param horasSesion duración máxima de una sesión no persistente
 * @param diasSesionPersistente duración de «mantener sesión» en el móvil (CU-MOD01-001 5a)
 * @param diasSinUso días sin acceso para destacar una cuenta (CU-MOD01-007; la HU dice 75)
 * @param diasAvisoVigencia antelación del aviso de fin de vigencia (HU-MOD01-004)
 * @param cookieSegura {@code false} solo en desarrollo local sin HTTPS
 */
@ConfigurationProperties("core.seguridad")
public record PropiedadesSeguridad(
        @DefaultValue("5") int intentosMaximos,
        @DefaultValue("15") int minutosBloqueo,
        @DefaultValue("30") int minutosInactividad,
        @DefaultValue("12") int horasSesion,
        @DefaultValue("30") int diasSesionPersistente,
        @DefaultValue("60") int diasSinUso,
        @DefaultValue("7") int diasAvisoVigencia,
        @DefaultValue("true") boolean cookieSegura) {}
