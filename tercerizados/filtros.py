"""Lectura de parámetros de consulta (filtros y búsqueda) de los listados de MOD-05.

Cada filtro valida su valor y responde 400 con el formato de DRF `{parametro: [mensaje]}`. Los parámetros
desconocidos se ignoran (por ejemplo, `page`). Un valor vacío equivale a no filtrar.
"""
from dataclasses import dataclass
from typing import Any, Callable
from django.db.models import Q, QuerySet
from drf_spectacular.types import OpenApiTypes
from drf_spectacular.utils import OpenApiParameter
from rest_framework.exceptions import ValidationError

LARGO_MAXIMO_BUSQUEDA = 100
TERMINOS_MAXIMOS = 8


def entero(campo: str, texto: str) -> int:
    try:
        valor = int(texto)
    except ValueError:
        valor = 0
    if valor < 1:
        raise ValidationError({campo: ['Indica un número entero positivo']})
    return valor


def booleano(campo: str, texto: str) -> bool:
    valor = texto.strip().lower()
    if valor in ('true', '1'):
        return True
    if valor in ('false', '0'):
        return False
    raise ValidationError({campo: ['Usa true o false']})


def opcion(opciones) -> Callable[[str, str], str]:
    validas = {str(o) for o in opciones}

    def convertir(campo: str, texto: str) -> str:
        if texto not in validas:
            raise ValidationError({campo: [f'Valor no válido. Opciones: {", ".join(sorted(validas))}']})
        return texto
    return convertir


@dataclass(frozen=True)
class Filtro:
    nombre: str
    convertir: Callable[[str, str], Any]
    tipo: Any
    descripcion: str
    campo: str | None = None
    aplicar: Callable[[QuerySet, Any], QuerySet] | None = None

    def filtrar(self, consulta: QuerySet, valor: Any) -> QuerySet:
        if self.aplicar:
            return self.aplicar(consulta, valor)
        return consulta.filter(**{self.campo or self.nombre: valor})


def filtro_entero(nombre: str, descripcion: str, campo: str | None = None) -> Filtro:
    return Filtro(nombre, entero, OpenApiTypes.INT, descripcion, campo)


def filtro_booleano(nombre: str, descripcion: str, campo: str | None = None, aplicar=None) -> Filtro:
    return Filtro(nombre, booleano, OpenApiTypes.BOOL, descripcion, campo, aplicar)


def filtro_opcion(nombre: str, opciones, descripcion: str, campo: str | None = None) -> Filtro:
    return Filtro(nombre, opcion(opciones), OpenApiTypes.STR, descripcion, campo)


def aplicar_busqueda(consulta: QuerySet, texto: str, campos) -> QuerySet:
    """Cada palabra debe aparecer en alguno de los campos (sin distinguir mayúsculas)."""
    for termino in texto.split()[:TERMINOS_MAXIMOS]:
        condicion = Q()
        for campo in campos:
            condicion |= Q(**{f'{campo}__icontains': termino})
        consulta = consulta.filter(condicion)
    return consulta


def parametros_openapi(filtros, busqueda_campos=()):
    parametros = [OpenApiParameter(f.nombre, f.tipo, OpenApiParameter.QUERY, required=False, description=f.descripcion)
                  for f in filtros]
    if busqueda_campos:
        parametros.append(OpenApiParameter('q', OpenApiTypes.STR, OpenApiParameter.QUERY, required=False,
            description=f'Búsqueda por palabras (máx. {LARGO_MAXIMO_BUSQUEDA} caracteres) en: {", ".join(busqueda_campos)}'))
    return parametros


def filtrar(consulta: QuerySet, parametros, filtros=(), busqueda_campos=()) -> QuerySet:
    """Aplica `q` y los filtros declarados a `consulta`. `parametros` es `request.query_params`."""
    if busqueda_campos:
        texto = (parametros.get('q') or '').strip()
        if len(texto) > LARGO_MAXIMO_BUSQUEDA:
            raise ValidationError({'q': [f'La búsqueda admite hasta {LARGO_MAXIMO_BUSQUEDA} caracteres']})
        if texto:
            consulta = aplicar_busqueda(consulta, texto, busqueda_campos)
    for filtro in filtros:
        texto = parametros.get(filtro.nombre)
        if texto is None or not texto.strip():
            continue
        consulta = filtro.filtrar(consulta, filtro.convertir(filtro.nombre, texto.strip()))
    return consulta
