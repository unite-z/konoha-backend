package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

/** Entidad que no está en el registro de entidades ({@code ValueError} en el Django). */
public class EntidadDesconocida extends IllegalArgumentException {

    public EntidadDesconocida(String entidad) {
        super("Entidad desconocida: '" + entidad + "'");
    }
}
