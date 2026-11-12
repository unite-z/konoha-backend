package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import pe.edu.pucp.areasverdes.mod06.gestion.datos.ProductoAplicado;

/**
 * Agrupación de los productos aplicados por los proveedores (RF-REP-005, flujo 3a), en funciones
 * puras sin Spring: un total por producto y proveedor, ordenado por producto y luego por proveedor
 * con las reglas del español.
 */
public final class AgrupadorProductos {

    private AgrupadorProductos() {}

    public static List<TotalProductoProveedor> porProductoYProveedor(
            List<ProductoAplicado> aplicaciones) {
        Map<List<Long>, Grupo> grupos = new LinkedHashMap<>();
        for (ProductoAplicado a : aplicaciones) {
            grupos.computeIfAbsent(List.of(a.productoId(), a.proveedorId()), k -> new Grupo(a))
                    .sumar(a);
        }
        Collator textos = Collator.getInstance(Locale.forLanguageTag("es"));
        return grupos.values().stream()
                .map(Grupo::total)
                .sorted(
                        Comparator.comparing(TotalProductoProveedor::producto, textos)
                                .thenComparing(TotalProductoProveedor::proveedor, textos))
                .toList();
    }

    private static final class Grupo {
        private final ProductoAplicado primero;
        private final Set<Long> atencionIds = new LinkedHashSet<>();
        private final List<String> codigos = new ArrayList<>();
        private final Set<String> dosis = new LinkedHashSet<>();

        Grupo(ProductoAplicado primero) {
            this.primero = primero;
        }

        void sumar(ProductoAplicado a) {
            if (atencionIds.add(a.atencionId())) codigos.add(a.atencionCodigo());
            if (a.dosis() != null && !a.dosis().isBlank()) dosis.add(a.dosis().strip());
        }

        TotalProductoProveedor total() {
            String principio = primero.principioActivo();
            return new TotalProductoProveedor(
                    primero.productoId(),
                    primero.producto(),
                    principio == null || principio.isBlank() ? null : principio,
                    primero.proveedorId(),
                    primero.proveedor(),
                    atencionIds.size(),
                    List.copyOf(dosis),
                    List.copyOf(codigos));
        }
    }
}
