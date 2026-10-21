"""
Fuente de datos real (`DATA_SOURCE=db`): **aún no disponible**.

Plan:
- Cada método usará el ORM de los modelos del módulo propietario de la entidad (Modelo de BD v1, tabla 2) y
  devolverá `dict` con `.values()`, con los mismos nombres de atributo que hoy usan los JSON de `mock-data/`.
  Así los servicios no cambian.
- `crear` y `actualizar` seguirán limitados a SugerenciaIA y ReporteGenerado (entidades propias del MOD-06).
- El contenido de los archivos se leerá del almacenamiento de RT-003 (servicio de objetos, por definir).

Mientras tanto, todos los métodos lanzan `FuenteDatosNoDisponible` (HTTP 503).
"""

from __future__ import annotations

from typing import Any, NoReturn

from ..comun.errores import FuenteDatosNoDisponible
from .base import FuenteDatos

MENSAJE_BD_NO_DISPONIBLE = (
    "La fuente de datos 'db' aún no está disponible: faltan los modelos de los módulos propietarios "
    "y la base PostgreSQL con PostGIS. Usa DATA_SOURCE=mock"
)


def _no_disponible() -> NoReturn:
    raise FuenteDatosNoDisponible(MENSAJE_BD_NO_DISPONIBLE)


class FuenteDatosBD(FuenteDatos):
    """Estrategia de base de datos (pendiente de los modelos de MOD-01 a MOD-05 y de PostgreSQL/PostGIS)."""

    def listar(self, entidad: str, /, **igual_a: Any) -> list[dict]:
        _no_disponible()

    def obtener(self, entidad: str, id: int) -> dict | None:
        _no_disponible()

    def crear(self, entidad: str, datos: dict) -> dict:
        _no_disponible()

    def actualizar(self, entidad: str, id: int, cambios: dict) -> dict:
        _no_disponible()

    def leer_contenido_archivo(self, archivo: dict) -> bytes:
        _no_disponible()
