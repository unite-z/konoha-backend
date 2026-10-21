"""
Base de las pruebas del MOD-06.

- `FuenteDatosMemoria`: doble en memoria de `FuenteDatos`, con las mismas restricciones de escritura. Registra
  las escrituras para comprobar que los reportes no escriben nada.
- `ClienteIAFalso`: devuelve una propuesta fija o lanza `ServicioIANoDisponible`. **Ninguna prueba llama a Gemini.**
- Las pruebas no leen `mock-data/` (salvo `test_fuentes_datos.py`, que usa un directorio temporal).
"""

from __future__ import annotations

import copy
from types import SimpleNamespace
from typing import Any

import pytest
from rest_framework.test import APIClient

from reportes_indicadores import fuentes_datos
from reportes_indicadores.comun.errores import RecursoNoEncontrado, ServicioIANoDisponible
from reportes_indicadores.fuentes_datos.base import (
    FuenteDatos,
    filtrar,
    siguiente_id,
    validar_entidad,
    validar_escritura,
)
from reportes_indicadores.fuentes_datos.entidades import CARPETA_POR_ENTIDAD
from reportes_indicadores.ia import cliente_gemini


class FuenteDatosMemoria(FuenteDatos):
    """Fuente de datos en memoria: `dict[entidad, list[dict]]` y `archivos: dict[ruta, bytes]`."""

    def __init__(self, datos: dict[str, list[dict]] | None = None, archivos: dict[str, bytes] | None = None) -> None:
        self.datos: dict[str, list[dict]] = {e: [] for e in CARPETA_POR_ENTIDAD}
        for entidad, registros in (datos or {}).items():
            validar_entidad(entidad)
            self.datos[entidad] = copy.deepcopy(registros)
        self.archivos = dict(archivos or {})
        self.escrituras: list[tuple[str, str, Any]] = []

    def listar(self, entidad: str, /, **igual_a: Any) -> list[dict]:
        validar_entidad(entidad)
        return filtrar(self.datos[entidad], igual_a)

    def obtener(self, entidad: str, id: int) -> dict | None:
        encontrados = self.listar(entidad, id=id)
        return encontrados[0] if encontrados else None

    def crear(self, entidad: str, datos: dict) -> dict:
        validar_escritura(entidad)
        nuevo = copy.deepcopy(datos)
        nuevo["id"] = siguiente_id(self.datos[entidad])
        self.datos[entidad].append(nuevo)
        self.escrituras.append(("crear", entidad, nuevo["id"]))
        return copy.deepcopy(nuevo)

    def actualizar(self, entidad: str, id: int, cambios: dict) -> dict:
        validar_escritura(entidad)
        for registro in self.datos[entidad]:
            if registro.get("id") == id:
                registro.update(copy.deepcopy({k: v for k, v in cambios.items() if k != "id"}))
                self.escrituras.append(("actualizar", entidad, id))
                return copy.deepcopy(registro)
        raise RecursoNoEncontrado(f"No existe el registro {id} de {entidad}")

    def leer_contenido_archivo(self, archivo: dict) -> bytes:
        ruta = archivo.get("ruta_almacenamiento")
        if ruta not in self.archivos:
            raise RecursoNoEncontrado("No se encontró el archivo del informe en el almacenamiento")
        return self.archivos[ruta]


class ClienteIAFalso:
    """Doble del cliente de IA. `propuesta` fija o `error` para simular un fallo."""

    proveedor = cliente_gemini.PROVEEDOR_IA

    def __init__(self, propuesta: dict | None = None, error: Exception | None = None, modelo: str = "modelo-de-prueba"):
        self.propuesta = propuesta or {}
        self.error = error
        self.modelo = modelo
        self.llamadas: list[dict] = []

    def extraer_json(self, pdf: bytes, esquema: dict, instruccion: str) -> dict:
        self.llamadas.append({"pdf": pdf, "esquema": esquema, "instruccion": instruccion})
        if self.error is not None:
            raise self.error
        return copy.deepcopy(self.propuesta)


@pytest.fixture
def usar_fuente(monkeypatch):
    """Instala una `FuenteDatosMemoria` como la fuente de datos de todo el back-end y la devuelve."""

    def _instalar(datos: dict[str, list[dict]] | None = None, archivos: dict[str, bytes] | None = None) -> FuenteDatosMemoria:
        fuente = FuenteDatosMemoria(datos, archivos)
        monkeypatch.setattr(fuentes_datos, "obtener_fuente_datos", lambda: fuente)
        return fuente

    return _instalar


@pytest.fixture
def usar_cliente_ia(monkeypatch):
    """Sustituye `obtener_cliente_ia()` por un `ClienteIAFalso`."""

    def _instalar(propuesta: dict | None = None, error: Exception | None = None) -> ClienteIAFalso:
        cliente = ClienteIAFalso(propuesta=propuesta, error=error)
        monkeypatch.setattr(cliente_gemini, "obtener_cliente_ia", lambda: cliente)
        return cliente

    return _instalar


@pytest.fixture
def api_client() -> APIClient:
    return APIClient()


@pytest.fixture
def usuario_con_rol(monkeypatch):
    """
    Devuelve un `request.user` simulado autenticado con el rol indicado, parcheando `rol_de_usuario`.
    Uso: `api_client.force_authenticate(user=usuario_con_rol("Ingeniero de campo"))`.
    """
    from reportes_indicadores import permisos

    def _crear(rol: str, id_usuario: int = 99) -> SimpleNamespace:
        usuario = SimpleNamespace(is_authenticated=True, id=id_usuario, rol=rol)
        monkeypatch.setattr(permisos, "rol_de_usuario", lambda user: getattr(user, "rol", None))
        return usuario

    return _crear


@pytest.fixture
def sin_ia_real(monkeypatch):
    """Garantía adicional: si algo intentara crear el cliente real del SDK, la prueba falla."""

    def _prohibido(*args, **kwargs):
        raise AssertionError("Las pruebas no deben llamar a Gemini")

    monkeypatch.setattr(cliente_gemini.ClienteGemini, "extraer_json", _prohibido)


# --- Datos mínimos reutilizables -----------------------------------------------


def auditoria(fecha: str = "2026-01-02T08:00:00-05:00", usuario: int | None = 1) -> dict:
    return {
        "fecha_creacion": fecha,
        "usuario_creacion_id": usuario,
        "fecha_modificacion": fecha,
        "usuario_modificacion_id": usuario,
    }
