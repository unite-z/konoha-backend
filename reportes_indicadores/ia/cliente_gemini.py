"""
Adaptador de Gemini (componente de IA desacoplado, ADR-001 figura 1).

Es el **único** archivo que importa el SDK `google-genai`. Expone un método del dominio, `extraer_json`, y
oculta el SDK: si mañana cambia el proveedor (por ejemplo Ollama), solo cambia este archivo.

API usada (documentación oficial de la Gemini API, SDK `google-genai` 2.32.0):
- PDF en línea: https://ai.google.dev/gemini-api/docs/document-processing
  `client.interactions.create(model=..., input=[{"type": "document", "data": <base64>, "mime_type": "application/pdf"},
  {"type": "text", "text": ...}])`. Límite: 50 MB o 1 000 páginas, igual en línea que con el Files API.
- Salida JSON estructurada: https://ai.google.dev/gemini-api/docs/structured-output
  `response_format={"type": "text", "mime_type": "application/json", "schema": <JSON Schema>}` y lectura de
  `interaction.output_text`. Los campos que admiten nulo usan `{"type": [<tipo>, "null"]}`.
- Tiempo de espera: el valor por defecto del SDK (no se fija ninguno).

Seguridad: nunca se registra ni se devuelve el valor de `GEMINI_API_KEY` ni el contenido del PDF (RNF-008).
RN-IA-001: la salida es una propuesta; si el servicio falla, el flujo manual continúa (HU-MOD06-010).
"""

from __future__ import annotations

import base64
import json
import logging
from typing import Any

from django.conf import settings

from ..comun.errores import ServicioIANoDisponible

logger = logging.getLogger(__name__)

PROVEEDOR_IA = "Gemini (Google AI Studio)"
MENSAJE_IA_NO_CONFIGURADA = "El análisis con IA no está configurado; registra los datos manualmente"
MENSAJE_IA_FALLO = "No se pudo analizar el informe con IA; registra los datos manualmente o inténtalo más tarde"
TEXTO_SOLICITUD = "Extrae los datos del informe adjunto según el esquema indicado."


class ClienteGemini:
    """Cliente del dominio para Gemini: envía un PDF y recibe un JSON con el esquema pedido."""

    def __init__(self, api_key: str | None = None, modelo: str | None = None, cliente_sdk: Any = None) -> None:
        """
        Crea el cliente del SDK con la clave **explícita** (`settings.GEMINI_API_KEY`) y el modelo
        `settings.GEMINI_MODEL`. `cliente_sdk` permite inyectar un doble en las pruebas.
        """
        clave = settings.GEMINI_API_KEY if api_key is None else api_key
        nombre_modelo = settings.GEMINI_MODEL if modelo is None else modelo
        if not clave or not nombre_modelo:
            logger.error(
                "Análisis con IA no configurado: falta %s en backend/.env",
                "GEMINI_API_KEY" if not clave else "GEMINI_MODEL",
            )
            raise ServicioIANoDisponible(MENSAJE_IA_NO_CONFIGURADA)
        self._modelo = nombre_modelo
        if cliente_sdk is not None:
            self._cliente = cliente_sdk
        else:
            from google import genai

            self._cliente = genai.Client(api_key=clave)

    @property
    def proveedor(self) -> str:
        return PROVEEDOR_IA

    @property
    def modelo(self) -> str:
        """Nombre del modelo configurado (se conserva en `SugerenciaIA.contenido.ia.modelo`)."""
        return self._modelo

    def extraer_json(self, pdf: bytes, esquema: dict, instruccion: str) -> dict:
        """
        Envía el PDF a Gemini con la instrucción del sistema y pide salida JSON con `esquema`.

        Cualquier excepción del SDK, respuesta vacía o JSON no interpretable → `ServicioIANoDisponible`.
        """
        try:
            interaccion = self._cliente.interactions.create(
                model=self._modelo,
                system_instruction=instruccion,
                input=[
                    {
                        "type": "document",
                        "data": base64.b64encode(pdf).decode("utf-8"),
                        "mime_type": "application/pdf",
                    },
                    {"type": "text", "text": TEXTO_SOLICITUD},
                ],
                response_format={
                    "type": "text",
                    "mime_type": "application/json",
                    "schema": esquema,
                },
            )
        except Exception as exc:  # cualquier error del SDK o de red
            logger.error("Fallo en la llamada a Gemini (%s)", type(exc).__name__)
            raise ServicioIANoDisponible(MENSAJE_IA_FALLO) from None

        texto = getattr(interaccion, "output_text", None)
        if not texto or not str(texto).strip():
            logger.error("Gemini devolvió una respuesta vacía")
            raise ServicioIANoDisponible(MENSAJE_IA_FALLO)
        try:
            datos = json.loads(texto)
        except (TypeError, ValueError):
            logger.error("Gemini devolvió un JSON no interpretable")
            raise ServicioIANoDisponible(MENSAJE_IA_FALLO) from None
        if not isinstance(datos, dict):
            logger.error("Gemini devolvió un JSON que no es un objeto")
            raise ServicioIANoDisponible(MENSAJE_IA_FALLO)
        return datos


def obtener_cliente_ia() -> ClienteGemini:
    """Único punto de creación del cliente de IA (se sustituye por un doble en las pruebas)."""
    return ClienteGemini()
