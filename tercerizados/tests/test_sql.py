from io import StringIO
from pathlib import Path
import pytest
from django.core.management import call_command
from django.db import connection


@pytest.mark.django_db(transaction=True)
def test_sql_generado_y_esquema_equivalente():
    ruta = Path(__file__).resolve().parents[1] / 'sql' / '0001_tercerizados.sql'
    salida = StringIO()
    call_command('exportar_sql_tercerizados', stdout=salida, no_color=True)
    sql = ruta.read_text()
    assert sql == salida.getvalue(), 'Regenerar SQL después de cambiar las migraciones'
    with connection.cursor() as cursor:
        cursor.execute('CREATE SCHEMA mod05_sql_test')
        try:
            cursor.execute('SET search_path TO mod05_sql_test, public')
            cursor.execute(sql)
            cursor.execute('SET search_path TO public')
            def esquema(nombre):
                cursor.execute('''SELECT table_name, column_name, data_type, is_nullable,
                    character_maximum_length, numeric_precision, numeric_scale
                    FROM information_schema.columns
                    WHERE table_schema = %s AND table_name LIKE 'tercerizados_%%'
                    ORDER BY table_name, ordinal_position''', [nombre])
                columnas = cursor.fetchall()
                cursor.execute('''SELECT t.relname, c.conname, c.contype, pg_get_constraintdef(c.oid)
                    FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                    JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=%s AND t.relname LIKE 'tercerizados_%%'
                    ORDER BY t.relname, c.conname''', [nombre])
                restricciones = [tuple(str(v).replace('mod05_sql_test.', '') for v in row) for row in cursor.fetchall()]
                cursor.execute('''SELECT t.relname, g.tgname FROM pg_trigger g
                    JOIN pg_class t ON t.oid=g.tgrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                    WHERE n.nspname=%s AND NOT g.tgisinternal ORDER BY t.relname, g.tgname''', [nombre])
                triggers = cursor.fetchall()
                return columnas, restricciones, triggers
            assert esquema('public') == esquema('mod05_sql_test')
        finally:
            cursor.execute('SET search_path TO public')
            cursor.execute('DROP SCHEMA mod05_sql_test CASCADE')
