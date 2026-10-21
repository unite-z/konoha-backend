"""
Capa de servicios del MOD-06: reglas de negocio de RF-REP-002, RF-REP-004 y RF-REP-006.

ADR-001, figura 1: «Servicios por módulo MOD-01 a MOD-06. Reglas de negocio».
Los servicios no conocen HTTP ni DRF y **no modifican ningún registro operativo** (CU-MOD06-001/002).
Reciben la fuente de datos por parámetro; si no se indica, usan `obtener_fuente_datos()`.
"""

from __future__ import annotations

from ..fuentes_datos.base import FuenteDatos


def indicador_por_codigo(fuente: FuenteDatos, codigo: str) -> dict | None:
    """
    DefinicionIndicador con ese `codigo` como `{codigo, nombre, descripcion_calculo, meta, unidad}`, o `None`.

    Diagrama de Clases v1: DefinicionIndicador.calcular(periodo). Que no exista no es un error.
    """
    definiciones = fuente.listar("DefinicionIndicador", codigo=codigo)
    if not definiciones:
        return None
    definicion = definiciones[0]
    return {
        "codigo": definicion.get("codigo"),
        "nombre": definicion.get("nombre"),
        "descripcion_calculo": definicion.get("descripcion_calculo"),
        "meta": definicion.get("meta"),
        "unidad": definicion.get("unidad"),
    }


def nombre_completo(persona: dict | None) -> str | None:
    """`nombres apellidos` de un registro de Personal (sin datos personales sensibles, RNF-008)."""
    if not persona:
        return None
    partes = [persona.get("nombres") or "", persona.get("apellidos") or ""]
    texto = " ".join(p for p in partes if p).strip()
    return texto or None
