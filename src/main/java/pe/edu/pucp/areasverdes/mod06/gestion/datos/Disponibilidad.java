package pe.edu.pucp.areasverdes.mod06.gestion.datos;

import java.util.Objects;

/**
 * Si una fuente de datos de otro módulo ya existe en la base. Si no, {@code motivo} dice por qué no
 * hay datos (Guía de Estilo Web 5.7: «Sin datos» con su motivo, nunca un 0).
 */
public record Disponibilidad(boolean disponible, String motivo) {

    /**
     * La fuente existe. Es una constante y no un método {@code disponible()}, que chocaría con el
     * accesor del componente del mismo nombre.
     */
    public static final Disponibilidad DISPONIBLE = new Disponibilidad(true, null);

    public static Disponibilidad noDisponible(String motivo) {
        Objects.requireNonNull(motivo, "motivo");
        if (motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo de «Sin datos» no puede estar en blanco");
        }
        return new Disponibilidad(false, motivo);
    }
}
