"""
Interfaz común de las fuentes de datos del MOD-06 (patrón Estrategia).

Hay dos estrategias concretas, `FuenteDatosMock` (JSON en `mock-data/`) y `FuenteDatosBD` (ORM, aún no
disponible). `obtener_fuente_datos()` elige una según `settings.DATA_SOURCE`.

Los registros circulan como `dict` con claves `snake_case` idénticas a los atributos del Modelo de BD v1,
de modo que los servicios funcionan igual con las dos fuentes.

Reglas que hace cumplir la interfaz:
- Entidad desconocida → `ValueError`.
- Escritura en una entidad que no es del MOD-06 → `PermissionError` (Modelo de BD v1, sección 2.1).

Filtrado en memoria: RNF-013 declara volúmenes pequeños (unas 35 personas, más de 100 jardines,
1 084 ejemplares) y ADR-001, criterio C9, dice que «ninguna opción tiene problemas de capacidad previsibles».
"""

from __future__ import annotations

import copy
from abc import ABC, abstractmethod
from typing import Any, Iterable

from .entidades import CARPETA_POR_ENTIDAD, ENTIDADES_ESCRIBIBLES

MENSAJE_ESCRITURA_NO_PERMITIDA = (
    "El MOD-06 solo escribe en SugerenciaIA y ReporteGenerado (Modelo de BD v1, sección 2.1)"
)


def validar_entidad(entidad: str) -> None:
    """Lanza `ValueError` si `entidad` no está en el registro de entidades."""
    if entidad not in CARPETA_POR_ENTIDAD:
        raise ValueError(f"Entidad desconocida: '{entidad}'")


def validar_escritura(entidad: str) -> None:
    """Lanza `ValueError` si la entidad no existe y `PermissionError` si no es una entidad propia del MOD-06."""
    validar_entidad(entidad)
    if entidad not in ENTIDADES_ESCRIBIBLES:
        raise PermissionError(MENSAJE_ESCRITURA_NO_PERMITIDA)


def filtrar(registros: Iterable[dict], igual_a: dict[str, Any]) -> list[dict]:
    """Copias profundas de los registros cuya clave coincide con el valor para cada par de `igual_a`."""
    return [
        copy.deepcopy(registro)
        for registro in registros
        if all(registro.get(clave) == valor for clave, valor in igual_a.items())
    ]


def siguiente_id(registros: Iterable[dict]) -> int:
    """`max(id) + 1`, o 1 si no hay registros."""
    return max((int(r["id"]) for r in registros if r.get("id") is not None), default=0) + 1


class FuenteDatos(ABC):
    """Interfaz de acceso a datos del MOD-06."""

    @abstractmethod
    def listar(self, entidad: str, /, **igual_a: Any) -> list[dict]:
        """
        Devuelve copias de los registros cuya clave == valor para cada par de `igual_a`.

        `entidad` es solo posicional para poder filtrar por atributos que se llaman `entidad`
        (ArchivoAsociado, SugerenciaIA).
        """

    @abstractmethod
    def obtener(self, entidad: str, id: int) -> dict | None:
        """Devuelve una copia del registro con ese `id`, o `None` si no existe."""

    @abstractmethod
    def crear(self, entidad: str, datos: dict) -> dict:
        """Solo `ENTIDADES_ESCRIBIBLES`. Asigna `id = max(id) + 1`. Devuelve el registro creado."""

    @abstractmethod
    def actualizar(self, entidad: str, id: int, cambios: dict) -> dict:
        """Solo `ENTIDADES_ESCRIBIBLES`. Error (`RecursoNoEncontrado`) si no existe. Devuelve el registro actualizado."""

    @abstractmethod
    def leer_contenido_archivo(self, archivo: dict) -> bytes:
        """
        Devuelve el contenido binario de un registro de la entidad Archivo.

        RT-003 y RI-33: el contenido vive fuera de la base de datos; `archivo` solo tiene el metadato y la ruta.
        """
