from django.db import migrations

SQL = """
CREATE FUNCTION mod05_rechazar_cambio_historial() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El historial MOD-05 solo permite inserciones';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER mod05_auditoria_inmutable BEFORE UPDATE OR DELETE ON tercerizados_registroauditoria
FOR EACH ROW EXECUTE FUNCTION mod05_rechazar_cambio_historial();
CREATE TRIGGER mod05_estados_inmutables BEFORE UPDATE OR DELETE ON tercerizados_historialestado
FOR EACH ROW EXECUTE FUNCTION mod05_rechazar_cambio_historial();
"""
REVERSE = """
DROP TRIGGER mod05_auditoria_inmutable ON tercerizados_registroauditoria;
DROP TRIGGER mod05_estados_inmutables ON tercerizados_historialestado;
DROP FUNCTION mod05_rechazar_cambio_historial();
"""


class Migration(migrations.Migration):
    dependencies = [('tercerizados', '0001_initial')]
    operations = [migrations.RunSQL(SQL, REVERSE)]
