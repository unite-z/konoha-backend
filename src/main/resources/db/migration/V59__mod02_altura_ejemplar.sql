-- AV-315 / dependencia AV-133: dato dasométrico mínimo para aplicar RN-005.
ALTER TABLE mod02_ejemplar
    ADD COLUMN altura_total_m numeric(6,2),
    ADD CONSTRAINT mod02_ejemplar_altura_positiva
        CHECK (altura_total_m IS NULL OR altura_total_m > 0);
