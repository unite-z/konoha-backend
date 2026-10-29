package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import java.util.List;

/** FeatureCollection de GeoJSON (RFC 7946); {@code type} = «FeatureCollection». */
public record ColeccionGeoJson(String type, List<ElementoGeoJson> features) {

    public static ColeccionGeoJson de(List<ElementoGeoJson> features) {
        return new ColeccionGeoJson("FeatureCollection", List.copyOf(features));
    }
}
