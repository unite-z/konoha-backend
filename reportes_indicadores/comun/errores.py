"""
Excepciones de dominio del MOD-06.

Los servicios lanzan estas excepciones sin saber nada de HTTP. La capa `api/manejador_excepciones.py`
las traduce al código HTTP correspondiente (superprompt, sección 4.5) con el cuerpo
`{"codigo": "<NombreExcepcion>", "detalle": "<mensaje que dice qué hacer>"}`.

Los mensajes siguen la Guía de Estilo Web (sección 7): dicen qué pasó y qué hacer.
"""

from __future__ import annotations


class ErrorDominio(Exception):
    """Base de las excepciones de dominio. `mensaje` es el texto que verá el usuario."""

    def __init__(self, mensaje: str) -> None:
        super().__init__(mensaje)
        self.mensaje = mensaje


class ParametroInvalido(ErrorDominio):
    """Parámetro o cuerpo de la petición no válido (400)."""


class RecursoNoEncontrado(ErrorDominio):
    """Identificador inexistente: reporte, archivo o sugerencia (404)."""


class TransicionNoPermitida(ErrorDominio):
    """Validar o rechazar una SugerenciaIA que no está en `Propuesta` (409, RI-32)."""


class ConfiguracionFaltante(ErrorDominio):
    """Falta un dato configurable necesario, por ejemplo el formato estándar vigente (422)."""


class TipoArchivoNoAdmitido(ErrorDominio):
    """El archivo del informe no es PDF (415)."""


class FuenteDatosNoDisponible(ErrorDominio):
    """`DATA_SOURCE=db` aún no implementado o error al leer los datos (503, CU-MOD06-001 E1: permite reintentar)."""


class ServicioIANoDisponible(ErrorDominio):
    """
    Falta la configuración de Gemini, o la llamada falló, agotó el tiempo o devolvió una respuesta inválida (503).

    HU-MOD06-010: el error se informa y se permite continuar con el registro manual.
    """
