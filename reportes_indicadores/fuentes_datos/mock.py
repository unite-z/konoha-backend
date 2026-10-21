"""
Fuente de datos simulados: lee `mock-data/<carpeta>/<Entidad>.json` desde `settings.MOCK_DATA_DIR`.

- Lectura sin caché: los JSON pueden editarse a mano durante las pruebas.
- Escritura solo en las entidades propias del MOD-06 (SugerenciaIA, ReporteGenerado), bajo un candado del
  proceso y con reemplazo atómico del archivo (`os.replace`).
- `leer_contenido_archivo` no permite salir de `MOCK_DATA_DIR` (rechaza rutas absolutas o con `..`).

Requisitos que atiende: RF-REP-002, RF-REP-004, RF-REP-006 y RF-IA-001 (como fuente de datos).
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
import threading
from pathlib import Path, PurePosixPath, PureWindowsPath
from typing import Any

from django.conf import settings

from ..comun.errores import FuenteDatosNoDisponible, RecursoNoEncontrado
from .base import FuenteDatos, filtrar, siguiente_id, validar_entidad, validar_escritura
from .entidades import CARPETA_POR_ENTIDAD

logger = logging.getLogger(__name__)

MENSAJE_ARCHIVO_NO_ENCONTRADO = (
    "No se encontró el archivo del informe en el almacenamiento; súbelo de nuevo o registra los datos manualmente"
)

# Un solo candado por proceso para todas las escrituras en mock-data.
_CANDADO_ESCRITURA = threading.Lock()


class FuenteDatosMock(FuenteDatos):
    """Estrategia de datos simulados (`DATA_SOURCE=mock`)."""

    def __init__(self, directorio: Path | str | None = None) -> None:
        self._directorio_fijo = Path(directorio) if directorio is not None else None

    # --- utilidades internas ------------------------------------------------

    @property
    def directorio(self) -> Path:
        """Directorio raíz de los datos simulados (se lee de `settings` en cada uso)."""
        return (self._directorio_fijo or Path(settings.MOCK_DATA_DIR)).resolve()

    def _ruta(self, entidad: str) -> Path:
        validar_entidad(entidad)
        return self.directorio / CARPETA_POR_ENTIDAD[entidad] / f"{entidad}.json"

    def _ruta_relativa(self, entidad: str) -> str:
        return f"{CARPETA_POR_ENTIDAD[entidad]}/{entidad}.json"

    def _leer(self, entidad: str) -> list[dict]:
        ruta = self._ruta(entidad)
        try:
            with ruta.open(encoding="utf-8") as archivo:
                datos = json.load(archivo)
        except FileNotFoundError:
            logger.error("Falta el archivo de datos simulados %s", self._ruta_relativa(entidad))
            raise FuenteDatosNoDisponible(
                f"No se encontraron los datos de {entidad} ({self._ruta_relativa(entidad)}); "
                "revisa la carpeta mock-data e inténtalo de nuevo"
            ) from None
        except (json.JSONDecodeError, UnicodeDecodeError, OSError):
            logger.error("No se pudo leer el archivo de datos simulados %s", self._ruta_relativa(entidad))
            raise FuenteDatosNoDisponible(
                f"No se pudieron leer los datos de {entidad} ({self._ruta_relativa(entidad)}); "
                "revisa que el JSON sea válido e inténtalo de nuevo"
            ) from None
        if not isinstance(datos, list):
            raise FuenteDatosNoDisponible(
                f"Los datos de {entidad} ({self._ruta_relativa(entidad)}) deben ser un arreglo JSON; "
                "corrige el archivo e inténtalo de nuevo"
            )
        return datos

    def _escribir_atomico(self, entidad: str, registros: list[dict]) -> None:
        ruta = self._ruta(entidad)
        descriptor, ruta_temporal = tempfile.mkstemp(
            prefix=f".{entidad}.", suffix=".tmp", dir=ruta.parent
        )
        try:
            with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as archivo:
                json.dump(registros, archivo, indent=2, ensure_ascii=False)
                archivo.write("\n")
            os.replace(ruta_temporal, ruta)
        except BaseException:
            if os.path.exists(ruta_temporal):
                os.remove(ruta_temporal)
            raise

    # --- interfaz FuenteDatos ----------------------------------------------

    def listar(self, entidad: str, /, **igual_a: Any) -> list[dict]:
        return filtrar(self._leer(entidad), igual_a)

    def obtener(self, entidad: str, id: int) -> dict | None:
        encontrados = filtrar(self._leer(entidad), {"id": id})
        return encontrados[0] if encontrados else None

    def crear(self, entidad: str, datos: dict) -> dict:
        validar_escritura(entidad)
        with _CANDADO_ESCRITURA:
            registros = self._leer(entidad)
            nuevo = dict(datos)
            nuevo["id"] = siguiente_id(registros)
            registros.append(nuevo)
            self._escribir_atomico(entidad, registros)
        return filtrar([nuevo], {})[0]

    def actualizar(self, entidad: str, id: int, cambios: dict) -> dict:
        validar_escritura(entidad)
        with _CANDADO_ESCRITURA:
            registros = self._leer(entidad)
            for registro in registros:
                if registro.get("id") == id:
                    registro.update({k: v for k, v in cambios.items() if k != "id"})
                    self._escribir_atomico(entidad, registros)
                    return filtrar([registro], {})[0]
        raise RecursoNoEncontrado(f"No existe el registro {id} de {entidad}")

    def leer_contenido_archivo(self, archivo: dict) -> bytes:
        ruta_texto = str(archivo.get("ruta_almacenamiento") or "")
        if not ruta_texto or not _es_ruta_relativa_segura(ruta_texto):
            logger.warning("Ruta de almacenamiento rechazada para el archivo %s", archivo.get("id"))
            raise RecursoNoEncontrado(MENSAJE_ARCHIVO_NO_ENCONTRADO)
        raiz = self.directorio
        ruta = (raiz / ruta_texto).resolve()
        if not ruta.is_relative_to(raiz) or not ruta.is_file():
            raise RecursoNoEncontrado(MENSAJE_ARCHIVO_NO_ENCONTRADO)
        return ruta.read_bytes()


def _es_ruta_relativa_segura(ruta: str) -> bool:
    """`False` si la ruta es absoluta (POSIX o Windows) o contiene `..`."""
    for forma in (PurePosixPath(ruta), PureWindowsPath(ruta)):
        if forma.is_absolute() or forma.anchor or ".." in forma.parts:
            return False
    return True
