"""Exporta el SQL PostgreSQL de las migraciones, sin ejecutarlo."""
from io import StringIO
from django.core.management import call_command
from django.core.management.base import BaseCommand


class Command(BaseCommand):
    help = 'Imprime el script SQL equivalente a las migraciones MOD-05'

    def handle(self, *args, **options):
        self.stdout.write('-- Generado con exportar_sql_tercerizados. Requiere tablas auth_user y contenttypes.\n'
                          '-- No ejecutar tras migrate: no sincroniza django_migrations. Ver README.\nBEGIN;')
        for migracion in ('0001_initial', '0002_historial_inmutable'):
            salida = StringIO()
            call_command('sqlmigrate', 'tercerizados', migracion, stdout=salida, no_color=True)
            lineas = [linea for linea in salida.getvalue().splitlines() if linea not in ('BEGIN;', 'COMMIT;')]
            self.stdout.write('\n'.join(lineas))
        self.stdout.write('COMMIT;')
