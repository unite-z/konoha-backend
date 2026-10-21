"""
Registro de entidades: carpeta del módulo propietario y entidades que el MOD-06 puede escribir.

Fuente: Modelo de BD v1, tabla 2 (módulo propietario de cada entidad) y sección 2.1
(«el módulo propietario mantiene la entidad y los demás solo la consultan»).
"""

from __future__ import annotations

_ENTIDADES_POR_CARPETA: dict[str, tuple[str, ...]] = {
    "mod01": ("Usuario", "Rol"),
    "mod02": (
        "Lugar",
        "SectorMantenimiento",
        "AsignacionSector",
        "Cuartel",
        "AreaVerde",
        "RutaCorte",
        "Ejemplar",
        "IntervencionArbolado",
    ),
    "mod03": (
        "TipoServicio",
        "FrecuenciaServicio",
        "DuracionServicio",
        "ProgramacionServicio",
        "CicloCorte",
    ),
    "mod04": (
        "Personal",
        "Cuadrilla",
        "AsignacionCuadrilla",
        "ClaseActividad",
        "TipoActividad",
        "UnidadMedida",
        "ParteDiario",
        "Actividad",
        "ParticipacionActividad",
    ),
    "mod05": (
        "Proveedor",
        "Contrato",
        "AtencionTercerizada",
        "AlcanceAtencion",
        "ReporteProveedor",
        "FormatoReporteServicio",
        "CampoFormatoReporte",
        "Plaga",
        "ProductoAgronomico",
        "AplicacionFitosanitaria",
        "AplicacionObjetivo",
    ),
    "mod06": ("DefinicionIndicador", "ReporteGenerado", "SugerenciaIA"),
    "transversal": ("Archivo", "ArchivoAsociado"),
}

#: Entidad → carpeta del módulo propietario dentro de `mock-data/`.
CARPETA_POR_ENTIDAD: dict[str, str] = {
    entidad: carpeta
    for carpeta, entidades in _ENTIDADES_POR_CARPETA.items()
    for entidad in entidades
}

#: Entidades propias del MOD-06: las únicas en las que este módulo puede escribir.
ENTIDADES_ESCRIBIBLES: frozenset[str] = frozenset({"SugerenciaIA", "ReporteGenerado"})

#: Asociaciones con clave compuesta (sin atributo `id`).
ENTIDADES_SIN_ID: frozenset[str] = frozenset({"ParticipacionActividad"})
