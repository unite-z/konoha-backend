"""
Cliente de Gemini.

Cliente único de Gemini con el SDK `google-genai`. Lee `GEMINI_API_KEY` y `GEMINI_MODEL` de `settings`.
Regla RN-IA-001: la salida es una propuesta; si el servicio falla, el flujo manual continúa.

Requisitos que atiende:
- RF-IA-001 (HU-MOD06-010, CU-MOD06-010, CP-TR-MOD06-010)

Entidades que leerá:
- AtencionTercerizada, Contrato, TipoServicio, ReporteProveedor, FormatoReporteServicio, CampoFormatoReporte, Archivo, ArchivoAsociado, SugerenciaIA, Plaga, ProductoAgronomico, UnidadMedida, Usuario

TODO: implementar
"""
