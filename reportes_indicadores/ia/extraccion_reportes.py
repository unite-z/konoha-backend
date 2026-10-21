"""
Servicio de RF-IA-001: extracción asistida por IA de los informes de proveedores tercerizados.

Trazabilidad: RF-IA-001 · HU-MOD06-010 · CU-MOD06-010 · CP-TR-MOD06-010 · RN-IA-001 · RI-32 ·
CU-MOD05-006 (campos del reporte) · CU-MOD05-009 (formato estándar) · CU-MOD05-025 1a (validación de cada dato).

Texto del requisito (Catálogo v2): el sistema deberá permitir analizar mediante IA los reportes y documentos
entregados por los proveedores tercerizados para extraer y proponer información estructurada del servicio para
su revisión antes de incorporarla al registro oficial. La IA no registrará información de manera definitiva
sin validación humana.

Reglas transversales de IA (RN-IA-001):
- la salida de IA es una recomendación; la validación humana es obligatoria antes de cualquier efecto;
- el flujo convencional continúa si la IA no está disponible (`ServicioIANoDisponible` → registro manual);
- se distingue dato registrado, sugerencia (`Propuesta`) y decisión validada (`Validada` / `Rechazada`);
- se conservan las entradas relevantes y la salida de IA (dentro de `SugerenciaIA.contenido`);
- la función tiene métricas propias (`metricas()`).

Estados (RI-32, patrón Estado simplificado del Diagrama de Clases v1, sección 7.1):
`Propuesta` → `Validada` | `Rechazada`. No hay otras transiciones.

Pendiente (Análisis de backend, brecha 2): proponer al responsable de BD (Nicolás Ipanaque) columnas propias para
entradas, modelo y decisiones por campo. Mientras tanto, se guardan dentro de `contenido`.

Estructura de `contenido` (versión 1):
    {
      "version_contenido": 1,
      "entrada": {reporte_proveedor_id, atencion_id, tipo_servicio_id, formato_id, formato_version, archivo_id,
                  campos_solicitados: [{campo_id, nombre_campo, tipo_dato, obligatorio}]},
      "ia": {proveedor, modelo, solicitado_en, duracion_ms},
      "propuesta": {"campo_<id>": {nombre_campo, valor}},
      "campos_obligatorios_faltantes": ["campo_<id>"],
      "decision": null | {por_campo: {"campo_<id>": {accion, valor_final}},
                          resumen: {aceptados, corregidos, descartados}, motivo_rechazo}
    }
"""

from __future__ import annotations

import logging
import time
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

from .. import fuentes_datos
from ..comun.errores import (
    ConfiguracionFaltante,
    ParametroInvalido,
    RecursoNoEncontrado,
    TipoArchivoNoAdmitido,
    TransicionNoPermitida,
)
from ..comun.fechas import a_fecha_hora, ahora_iso
from ..fuentes_datos.base import FuenteDatos
from . import cliente_gemini

logger = logging.getLogger(__name__)

# --- Valores del Modelo de BD v1 ------------------------------------------------

ENTIDAD_SUGERENCIA = "SugerenciaIA"
TIPO_EXTRACCION = "ExtraccionReporte"  # TipoSugerenciaIA
ENTIDAD_REPORTE = "ReporteProveedor"
CATEGORIA_ARCHIVO_REPORTE = "ReporteProveedor"  # CategoriaArchivo
MIME_PDF = "application/pdf"

PROPUESTA = "Propuesta"
VALIDADA = "Validada"
RECHAZADA = "Rechazada"
ESTADOS_SUGERENCIA = (PROPUESTA, VALIDADA, RECHAZADA)

#: Transiciones permitidas (RI-32: «solo el validador cambia a Validada»).
TRANSICIONES_PERMITIDAS: dict[str, frozenset[str]] = {
    PROPUESTA: frozenset({VALIDADA, RECHAZADA}),
    VALIDADA: frozenset(),
    RECHAZADA: frozenset(),
}

ETIQUETAS_ESTADO = {PROPUESTA: "Pendiente de revisión", VALIDADA: "Validada", RECHAZADA: "Rechazada"}

ACEPTAR = "aceptar"
CORREGIR = "corregir"
DESCARTAR = "descartar"
ACCIONES = (ACEPTAR, CORREGIR, DESCARTAR)

VERSION_CONTENIDO = 1
_INSTANTE_MINIMO = datetime.min.replace(tzinfo=timezone.utc)
RUTA_INSTRUCCION = Path(__file__).resolve().parent / "prompts" / "extraccion_reporte_proveedor.md"

# --- Mensajes -------------------------------------------------------------------

MENSAJE_REPORTE_NO_EXISTE = "No existe el reporte del proveedor indicado; revisa el identificador"
MENSAJE_ATENCION_NO_EXISTE = (
    "El reporte no está asociado a una atención tercerizada existente; revisa el registro o registra los datos manualmente"
)
MENSAJE_SIN_FORMATO = (
    "El tipo de servicio no tiene formato estándar vigente; configúralo (RF-SER-009) o registra los datos manualmente"
)
MENSAJE_FORMATO_SIN_CAMPOS = (
    "El formato estándar vigente del servicio no tiene campos; configúralo (RF-SER-009) o registra los datos manualmente"
)
MENSAJE_SIN_ARCHIVO = "El reporte no tiene informe adjunto; adjúntalo o registra los datos manualmente"
MENSAJE_NO_PDF = "Solo se admite el informe en PDF; registra los datos manualmente o adjunta el informe en PDF"
MENSAJE_SUGERENCIA_NO_EXISTE = "No existe la propuesta de IA indicada; revisa el identificador"

# --- Esquema JSON por tipo de dato (tipos lógicos del Modelo de BD v1) ---------

_ESQUEMA_POR_TIPO: dict[str, dict] = {
    "Texto": {"type": ["string", "null"]},
    "Texto largo": {"type": ["string", "null"]},
    "Entero": {"type": ["integer", "null"]},
    "Decimal": {"type": ["number", "null"]},
    "Booleano": {"type": ["boolean", "null"]},
    "Fecha": {"type": ["string", "null"], "format": "date"},
    "JSON": {"type": ["array", "null"], "items": {"type": "object"}},
}
TIPOS_DATO_ADMITIDOS = tuple(_ESQUEMA_POR_TIPO)


# =============================================================================
# Operaciones públicas
# =============================================================================


def solicitar_extraccion(
    reporte_id: int,
    usuario_id: int | None,
    fuente: FuenteDatos | None = None,
    cliente: cliente_gemini.ClienteGemini | None = None,
) -> dict:
    """
    Analiza con IA el informe de un ReporteProveedor y crea una SugerenciaIA en estado `Propuesta`.

    Pasos 2 a 6 del flujo (HU-MOD06-010). Si la IA falla, se propaga `ServicioIANoDisponible` y no se crea
    ninguna sugerencia (el usuario continúa con el registro manual).
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()

    reporte = fuente.obtener("ReporteProveedor", reporte_id)
    if reporte is None:
        raise RecursoNoEncontrado(MENSAJE_REPORTE_NO_EXISTE)
    atencion = fuente.obtener("AtencionTercerizada", reporte.get("atencion_id"))
    if atencion is None:
        raise RecursoNoEncontrado(MENSAJE_ATENCION_NO_EXISTE)
    tipo_servicio_id = atencion.get("tipo_servicio_id")

    formato = _formato_vigente(fuente, tipo_servicio_id)
    campos = sorted(fuente.listar("CampoFormatoReporte", formato_id=formato["id"]), key=lambda c: c["id"])
    if not campos:
        raise ConfiguracionFaltante(MENSAJE_FORMATO_SIN_CAMPOS)
    esquema = construir_esquema(campos)

    archivo = _archivo_del_reporte(fuente, reporte["id"])
    if archivo.get("tipo_mime") != MIME_PDF:
        raise TipoArchivoNoAdmitido(MENSAJE_NO_PDF)

    # El cliente se obtiene antes de leer el archivo: si la IA no está configurada se informa de inmediato.
    cliente = cliente or cliente_gemini.obtener_cliente_ia()
    pdf = fuente.leer_contenido_archivo(archivo)
    instruccion = RUTA_INSTRUCCION.read_text(encoding="utf-8")

    solicitado_en = ahora_iso()
    inicio = time.monotonic()
    salida = cliente.extraer_json(pdf, esquema, instruccion)
    duracion_ms = int(round((time.monotonic() - inicio) * 1000))

    propuesta: dict[str, dict] = {}
    for campo in campos:
        clave = clave_campo(campo["id"])
        valor = salida.get(clave)
        if valor is not None and not valor_es_del_tipo(valor, campo.get("tipo_dato")):
            logger.warning("La IA propuso un valor de tipo no válido para %s; se deja sin valor", clave)
            valor = None
        propuesta[clave] = {"nombre_campo": campo.get("nombre_campo"), "valor": valor}

    contenido = {
        "version_contenido": VERSION_CONTENIDO,
        "entrada": {
            "reporte_proveedor_id": reporte["id"],
            "atencion_id": atencion["id"],
            "tipo_servicio_id": tipo_servicio_id,
            "formato_id": formato["id"],
            "formato_version": formato.get("version"),
            "archivo_id": archivo["id"],
            "campos_solicitados": [
                {
                    "campo_id": c["id"],
                    "nombre_campo": c.get("nombre_campo"),
                    "tipo_dato": c.get("tipo_dato"),
                    "obligatorio": bool(c.get("obligatorio")),
                }
                for c in campos
            ],
        },
        "ia": {
            "proveedor": getattr(cliente, "proveedor", cliente_gemini.PROVEEDOR_IA),
            "modelo": cliente.modelo,
            "solicitado_en": solicitado_en,
            "duracion_ms": duracion_ms,
        },
        "propuesta": propuesta,
        "campos_obligatorios_faltantes": [
            clave_campo(c["id"]) for c in campos if c.get("obligatorio") and _sin_valor(propuesta[clave_campo(c["id"])]["valor"])
        ],
        "decision": None,
    }

    instante = ahora_iso()
    sugerencia = fuente.crear(
        ENTIDAD_SUGERENCIA,
        {
            "tipo": TIPO_EXTRACCION,
            "entidad": ENTIDAD_REPORTE,
            "registro_id": reporte["id"],
            "archivo_id": archivo["id"],
            "contenido": contenido,
            "estado": PROPUESTA,
            "validador_id": None,
            "fecha_validacion": None,
            "fecha_creacion": instante,
            "usuario_creacion_id": usuario_id,
            "fecha_modificacion": instante,
            "usuario_modificacion_id": usuario_id,
        },
    )
    _registrar_auditoria("Alta", sugerencia, usuario_id)
    return sugerencia


def obtener(sugerencia_id: int, fuente: FuenteDatos | None = None) -> dict:
    """Detalle de una sugerencia de extracción. `RecursoNoEncontrado` si no existe."""
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    sugerencia = fuente.obtener(ENTIDAD_SUGERENCIA, sugerencia_id)
    if sugerencia is None or sugerencia.get("tipo") != TIPO_EXTRACCION:
        raise RecursoNoEncontrado(MENSAJE_SUGERENCIA_NO_EXISTE)
    return sugerencia


def listar(
    estado: str | None = None,
    reporte_proveedor_id: int | None = None,
    fuente: FuenteDatos | None = None,
) -> list[dict]:
    """Sugerencias `ExtraccionReporte`, opcionalmente por estado y reporte, de la más reciente a la más antigua."""
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    filtros: dict[str, Any] = {"tipo": TIPO_EXTRACCION}
    if estado is not None:
        filtros["estado"] = estado
    if reporte_proveedor_id is not None:
        filtros["entidad"] = ENTIDAD_REPORTE
        filtros["registro_id"] = reporte_proveedor_id
    sugerencias = fuente.listar(ENTIDAD_SUGERENCIA, **filtros)
    return sorted(
        sugerencias,
        key=lambda s: (a_fecha_hora(s.get("fecha_creacion")) or _INSTANTE_MINIMO, s.get("id") or 0),
        reverse=True,
    )


def validar(
    sugerencia_id: int,
    usuario_id: int | None,
    decisiones: dict[str, dict],
    fuente: FuenteDatos | None = None,
) -> dict:
    """
    Revisión campo por campo (CU-MOD05-025 1a): `aceptar`, `corregir` (con `valor_final`) o `descartar`.

    SugerenciaIA.validar(usuario) del Diagrama de Clases v1. Solo desde `Propuesta` (RI-32).
    Si todas las acciones son `descartar`, la sugerencia pasa a `Rechazada`; si no, a `Validada` y se invoca la
    incorporación al registro oficial (solo lo aceptado o corregido se incorpora, HU-MOD06-010).
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    sugerencia = obtener(sugerencia_id, fuente)
    _comprobar_transicion(sugerencia, VALIDADA)

    contenido = sugerencia.get("contenido") or {}
    propuesta: dict[str, dict] = contenido.get("propuesta") or {}
    tipos = {
        clave_campo(c["campo_id"]): c.get("tipo_dato")
        for c in (contenido.get("entrada") or {}).get("campos_solicitados", [])
    }

    if not isinstance(decisiones, dict):
        raise ParametroInvalido("Envía las decisiones como un objeto con una entrada por campo")
    faltan = sorted(set(propuesta) - set(decisiones))
    sobran = sorted(set(decisiones) - set(propuesta))
    if faltan or sobran:
        partes = []
        if faltan:
            partes.append(f"faltan: {', '.join(faltan)}")
        if sobran:
            partes.append(f"sobran: {', '.join(sobran)}")
        raise ParametroInvalido(
            "Indica una decisión para cada campo de la propuesta, y solo para ellos (" + "; ".join(partes) + ")"
        )

    por_campo: dict[str, dict] = {}
    for clave in propuesta:
        decision = decisiones[clave]
        nombre = propuesta[clave].get("nombre_campo") or clave
        accion = decision.get("accion") if isinstance(decision, dict) else None
        if accion not in ACCIONES:
            raise ParametroInvalido(
                f"La acción del campo '{nombre}' debe ser aceptar, corregir o descartar"
            )
        if accion == ACEPTAR:
            valor_final = propuesta[clave].get("valor")
        elif accion == CORREGIR:
            valor_final = decision.get("valor_final")
            if valor_final is None:
                raise ParametroInvalido(f"Indica el valor final del campo '{nombre}' para corregirlo")
            if not valor_es_del_tipo(valor_final, tipos.get(clave)):
                raise ParametroInvalido(
                    f"El valor final del campo '{nombre}' debe ser de tipo {tipos.get(clave)}"
                )
        else:
            valor_final = None
        por_campo[clave] = {"accion": accion, "valor_final": valor_final}

    acciones = [d["accion"] for d in por_campo.values()]
    nuevo_estado = RECHAZADA if acciones and all(a == DESCARTAR for a in acciones) else VALIDADA
    _comprobar_transicion(sugerencia, nuevo_estado)

    contenido["decision"] = {
        "por_campo": por_campo,
        "resumen": _resumen_acciones(acciones),
        "motivo_rechazo": None,
    }
    actualizada = _guardar_revision(fuente, sugerencia, nuevo_estado, contenido, usuario_id)
    if nuevo_estado == VALIDADA:
        _incorporar_al_registro_oficial(actualizada)
    return actualizada


def rechazar(
    sugerencia_id: int,
    usuario_id: int | None,
    motivo: str | None,
    fuente: FuenteDatos | None = None,
) -> dict:
    """
    Rechaza la propuesta completa (SugerenciaIA.rechazar(usuario), Diagrama de Clases v1). Solo desde `Propuesta`.

    Todas las acciones quedan como `descartar` y se guarda `motivo_rechazo`.
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    sugerencia = obtener(sugerencia_id, fuente)
    _comprobar_transicion(sugerencia, RECHAZADA)
    contenido = sugerencia.get("contenido") or {}
    claves = list((contenido.get("propuesta") or {}).keys())
    contenido["decision"] = {
        "por_campo": {clave: {"accion": DESCARTAR, "valor_final": None} for clave in claves},
        "resumen": _resumen_acciones([DESCARTAR] * len(claves)),
        "motivo_rechazo": (motivo or None),
    }
    return _guardar_revision(fuente, sugerencia, RECHAZADA, contenido, usuario_id)


def metricas(fuente: FuenteDatos | None = None) -> dict:
    """
    Métricas de la función de IA (reglas transversales: «métricas y escenarios de prueba propios»).

    - total por estado;
    - campos aceptados sin cambios, corregidos y descartados;
    - tasa de aceptación sin cambios = aceptados / (aceptados + corregidos + descartados), o `None`;
    - duración media de la llamada a la IA (`ia.duracion_ms`), o `None`.
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    sugerencias = fuente.listar(ENTIDAD_SUGERENCIA, tipo=TIPO_EXTRACCION)
    por_estado = {estado: 0 for estado in ESTADOS_SUGERENCIA}
    aceptados = corregidos = descartados = 0
    duraciones: list[int] = []
    for sugerencia in sugerencias:
        estado = sugerencia.get("estado")
        if estado in por_estado:
            por_estado[estado] += 1
        contenido = sugerencia.get("contenido") or {}
        duracion = (contenido.get("ia") or {}).get("duracion_ms")
        if isinstance(duracion, (int, float)):
            duraciones.append(duracion)
        decision = contenido.get("decision") or {}
        for valor in (decision.get("por_campo") or {}).values():
            accion = valor.get("accion")
            aceptados += accion == ACEPTAR
            corregidos += accion == CORREGIR
            descartados += accion == DESCARTAR
    total_decisiones = aceptados + corregidos + descartados
    return {
        "total_sugerencias": len(sugerencias),
        "por_estado": por_estado,
        "campos_aceptados": aceptados,
        "campos_corregidos": corregidos,
        "campos_descartados": descartados,
        "tasa_aceptacion_sin_cambios": round(aceptados / total_decisiones, 4) if total_decisiones else None,
        "duracion_media_ms": round(sum(duraciones) / len(duraciones), 1) if duraciones else None,
    }


def etiqueta_estado(estado: str | None) -> str | None:
    """Rótulo para la interfaz: `Propuesta` → «Pendiente de revisión» (HU-MOD06-010; Guía Web 7)."""
    return ETIQUETAS_ESTADO.get(estado, estado)


# =============================================================================
# Esquema y validación de tipos
# =============================================================================


def clave_campo(campo_id: int) -> str:
    """Clave estable de un campo dentro de `propuesta`: `campo_<id del CampoFormatoReporte>`."""
    return f"campo_{campo_id}"


def construir_esquema(campos: list[dict]) -> dict:
    """
    Esquema JSON pedido a Gemini: una propiedad `campo_<id>` por CampoFormatoReporte, todas admiten `null`.

    Un `tipo_dato` no admitido → `ConfiguracionFaltante` (422).
    """
    propiedades: dict[str, dict] = {}
    for campo in campos:
        tipo_dato = campo.get("tipo_dato")
        if tipo_dato not in _ESQUEMA_POR_TIPO:
            raise ConfiguracionFaltante(
                f"El campo '{campo.get('nombre_campo')}' del formato tiene un tipo de dato no admitido; "
                "corrige el formato estándar del servicio (RF-SER-009)"
            )
        esquema_campo = dict(_ESQUEMA_POR_TIPO[tipo_dato])
        esquema_campo["description"] = str(campo.get("nombre_campo") or "")
        propiedades[clave_campo(campo["id"])] = esquema_campo
    return {
        "type": "object",
        "properties": propiedades,
        "required": list(propiedades),
    }


def valor_es_del_tipo(valor: Any, tipo_dato: str | None) -> bool:
    """`True` si `valor` (no nulo) corresponde al tipo lógico `tipo_dato` del Modelo de BD v1."""
    if tipo_dato in ("Texto", "Texto largo"):
        return isinstance(valor, str)
    if tipo_dato == "Entero":
        return isinstance(valor, int) and not isinstance(valor, bool)
    if tipo_dato == "Decimal":
        return isinstance(valor, (int, float)) and not isinstance(valor, bool)
    if tipo_dato == "Booleano":
        return isinstance(valor, bool)
    if tipo_dato == "Fecha":
        if not isinstance(valor, str) or len(valor) != 10:
            return False
        try:
            date.fromisoformat(valor)
        except ValueError:
            return False
        return True
    if tipo_dato == "JSON":
        return isinstance(valor, list) and all(isinstance(elemento, dict) for elemento in valor)
    return False


# =============================================================================
# Puntos de integración con otros módulos
# =============================================================================


def _registrar_auditoria(accion: str, sugerencia: dict, usuario_id: int | None) -> None:
    """
    Registro de auditoría de la SugerenciaIA (acciones del enumerado AccionAuditoria: Alta, Modificacion,
    CambioEstado).

    TODO: delegar en RegistradorAuditoria (MOD-01, RF-T-008).
    """
    logger.info(
        "Auditoría: %s de SugerenciaIA id=%s estado=%s usuario_id=%s",
        accion,
        sugerencia.get("id"),
        sugerencia.get("estado"),
        usuario_id,
    )


def _incorporar_al_registro_oficial(sugerencia: dict) -> None:
    """
    Incorporación de los valores validados al registro oficial del MOD-05 (ReporteProveedor, AlcanceAtencion,
    AplicacionObjetivo, AplicacionProducto, AplicacionPlaga). El MOD-06 no escribe en esas entidades (Modelo
    de BD v1, sección 2.1): los valores quedan en la SugerenciaIA `Validada`, disponibles por la API.

    TODO: invocar el servicio de MOD-05 cuando publique su contrato (coordinar con Emely Munayco y Mafer Ramos).
    """
    logger.info(
        "SugerenciaIA id=%s validada: valores disponibles para que el MOD-05 los incorpore al registro oficial",
        sugerencia.get("id"),
    )


# =============================================================================
# Utilidades internas
# =============================================================================


def _formato_vigente(fuente: FuenteDatos, tipo_servicio_id: int | None) -> dict:
    formatos = fuente.listar("FormatoReporteServicio", tipo_servicio_id=tipo_servicio_id, vigente=True)
    if tipo_servicio_id is None or not formatos:
        raise ConfiguracionFaltante(MENSAJE_SIN_FORMATO)
    return max(formatos, key=lambda f: (f.get("version") or 0, f["id"]))


def _archivo_del_reporte(fuente: FuenteDatos, reporte_id: int) -> dict:
    asociaciones = fuente.listar(
        "ArchivoAsociado",
        entidad=ENTIDAD_REPORTE,
        registro_id=reporte_id,
        categoria=CATEGORIA_ARCHIVO_REPORTE,
    )
    for asociacion in sorted(asociaciones, key=lambda a: a["id"], reverse=True):
        archivo = fuente.obtener("Archivo", asociacion.get("archivo_id"))
        if archivo is not None:
            return archivo
    raise RecursoNoEncontrado(MENSAJE_SIN_ARCHIVO)


def _sin_valor(valor: Any) -> bool:
    return valor is None or valor == "" or valor == []


def _comprobar_transicion(sugerencia: dict, destino: str) -> None:
    estado = sugerencia.get("estado")
    if destino not in TRANSICIONES_PERMITIDAS.get(estado, frozenset()):
        raise TransicionNoPermitida(f"Esta propuesta ya fue revisada (estado actual: {estado})")


def _resumen_acciones(acciones: list[str]) -> dict[str, int]:
    return {
        "aceptados": acciones.count(ACEPTAR),
        "corregidos": acciones.count(CORREGIR),
        "descartados": acciones.count(DESCARTAR),
    }


def _guardar_revision(
    fuente: FuenteDatos, sugerencia: dict, nuevo_estado: str, contenido: dict, usuario_id: int | None
) -> dict:
    instante = ahora_iso()
    actualizada = fuente.actualizar(
        ENTIDAD_SUGERENCIA,
        sugerencia["id"],
        {
            "estado": nuevo_estado,
            "validador_id": usuario_id,
            "fecha_validacion": instante,
            "contenido": contenido,
            "fecha_modificacion": instante,
            "usuario_modificacion_id": usuario_id,
        },
    )
    _registrar_auditoria("CambioEstado", actualizada, usuario_id)
    return actualizada
