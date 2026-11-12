package pe.edu.pucp.areasverdes.mod06.gestion.servicios;

import com.fasterxml.jackson.databind.JsonNode;

/** Feature de GeoJSON (RFC 7946); {@code type} = «Feature». */
public record ElementoGeoJson(String type, JsonNode geometry, AtencionMapa properties) {

    public static ElementoGeoJson de(JsonNode geometria, AtencionMapa atencion) {
        return new ElementoGeoJson("Feature", geometria, atencion);
    }
}
