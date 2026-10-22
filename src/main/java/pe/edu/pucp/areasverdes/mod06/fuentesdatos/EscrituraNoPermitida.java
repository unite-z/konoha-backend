package pe.edu.pucp.areasverdes.mod06.fuentesdatos;

/** Escritura en una entidad que no es del MOD-06 ({@code PermissionError} en el Django). */
public class EscrituraNoPermitida extends RuntimeException {

    public EscrituraNoPermitida(String mensaje) {
        super(mensaje);
    }
}
