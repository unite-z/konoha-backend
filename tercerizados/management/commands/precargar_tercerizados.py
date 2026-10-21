"""Datos demostrativos; nunca importa atenciones históricas ni cambia contraseñas existentes."""
import json
from pathlib import Path
from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction, connection
from django.core.management.color import no_style
from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.core.exceptions import ValidationError
from tercerizados import models as m
from tercerizados.permisos import ROLES


class Command(BaseCommand):
    help = 'Precarga idempotente de referencias y usuarios MOD-05 (solo desarrollo)'

    def add_arguments(self, parser):
        parser.add_argument('--datos', type=Path, default=settings.MOCK_DATA_DIR)
        parser.add_argument('--usuarios', action='store_true', help='Crea usuarios con contraseña inutilizable; asignarla mediante changepassword')

    @transaction.atomic
    def handle(self, *args, **options):
        if not settings.DEBUG:
            raise CommandError('La precarga demostrativa requiere DJANGO_DEBUG=True')
        configuracion = [
            (m.Proveedor, 'mod05', ['razon_social', 'ruc', 'activo']),
            (m.TipoServicio, 'mod03', ['nombre', 'ejecutor', 'es_riesgo', 'activo']),
            (m.UnidadMedida, 'mod04', ['nombre', 'simbolo']),
            (m.AreaVerde, 'mod02', ['nombre', 'activo']),
            (m.Ejemplar, 'mod02', ['codigo', 'area_verde_id']),
            (m.Contrato, 'mod05', ['numero', 'proveedor_id', 'tipo_servicio_id', 'vigencia_inicio', 'vigencia_fin']),
        ]
        try:
            for modelo, carpeta, campos in configuracion:
                registros = json.loads((options['datos'] / carpeta / f'{modelo.__name__}.json').read_text())
                for fila in registros:
                    objeto, creado = modelo.objects.get_or_create(pk=fila['id'], defaults={k: fila[k] for k in campos})
                    # No sobrescribir registros operativos o catálogos corregidos.
                    if not creado and any(str(getattr(objeto, k)) != str(fila[k]) for k in campos):
                        raise CommandError(f'Conflicto en {modelo.__name__} id={fila["id"]}; no se sobrescriben datos existentes')
                    objeto.full_clean()
            m.UnidadInstitucional.objects.get_or_create(nombre='Sección de Áreas Verdes y Medio Ambiente')
            for rol in ROLES:
                Group.objects.get_or_create(name=rol)
            if options['usuarios']:
                for nombre, rol in zip(('mod05_jefe', 'mod05_ingeniero', 'mod05_practicante'), ROLES):
                    usuario, creado = get_user_model().objects.get_or_create(username=nombre)
                    if creado:
                        usuario.set_unusable_password()
                        usuario.save()
                        usuario.groups.add(Group.objects.get(name=rol))
            # Las PK explícitas de mocks no avanzan secuencias PostgreSQL.
            with connection.cursor() as cursor:
                for sentencia in connection.ops.sequence_reset_sql(no_style(), [x[0] for x in configuracion]):
                    cursor.execute(sentencia)
        except (OSError, ValueError, KeyError, ValidationError) as exc:
            raise CommandError(f'Precarga inválida; se revierte completamente: {exc}') from exc
        self.stdout.write(self.style.SUCCESS('Referencias y grupos preparados. Usa changepassword para habilitar usuarios de prueba.'))
