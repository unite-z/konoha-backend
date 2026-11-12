package pe.edu.pucp.areasverdes.mod01.servicios;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Política de contraseñas de CU-MOD01-002 (supuesto ACC-S6): al menos 8 caracteres, con letras y
 * números. La comparación con la actual la hace quien tiene el hash.
 */
public final class PoliticaContrasena {

    public static final int LONGITUD_MINIMA = 8;
    static final int LONGITUD_MAXIMA = 72; // límite de bcrypt

    private static final String LETRAS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITOS = "23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private PoliticaContrasena() {}

    /** Reglas que no cumple la contraseña, en el texto que se muestra al usuario (HU-MOD01-003). */
    public static List<String> incumplidas(String contrasena) {
        List<String> reglas = new ArrayList<>();
        String valor = contrasena == null ? "" : contrasena;
        if (valor.length() < LONGITUD_MINIMA) {
            reglas.add("Debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        if (valor.length() > LONGITUD_MAXIMA) {
            reglas.add("No puede tener más de " + LONGITUD_MAXIMA + " caracteres");
        }
        if (valor.chars().noneMatch(Character::isLetter)) {
            reglas.add("Debe incluir al menos una letra");
        }
        if (valor.chars().noneMatch(Character::isDigit)) {
            reglas.add("Debe incluir al menos un número");
        }
        return reglas;
    }

    /** Contraseña temporal de 12 caracteres que cumple la política. Se muestra una sola vez. */
    public static String temporal() {
        StringBuilder texto = new StringBuilder();
        texto.append(DIGITOS.charAt(AZAR.nextInt(DIGITOS.length())));
        texto.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
        String todos = LETRAS + DIGITOS;
        while (texto.length() < 12) {
            texto.append(todos.charAt(AZAR.nextInt(todos.length())));
        }
        return texto.toString();
    }
}
