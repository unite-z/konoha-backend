-- AV-366: el jefe marca o desmarca «solo servicios ruidosos» en cada restricción. Sin valor
-- (las restricciones anteriores y las que crea el sistema), vale el de su tipo.
ALTER TABLE mod03_restriccion_calendario ADD COLUMN aplica_servicios_ruidosos boolean;
