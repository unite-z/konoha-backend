"""
Servicio de RF-REP-004: distribución del personal por cuadrilla, actividad, espacio y jornada.

Trazabilidad: RF-REP-004 · HU-MOD06-002 · CU-MOD06-002 · sin caso de prueba en Casos de Prueba v2
(las pruebas de `tests/test_distribucion_personal.py` sirven de propuesta para QA) · RN-015, RI-04, RI-19,
RI-21 · CU-MOD04-011 (1a), CU-MOD04-013.

Texto del requisito (Catálogo v2): «El sistema deberá generar un reporte de distribución del personal por
cuadrilla, actividad, espacio y jornada».

Reglas [Definición acordada 20/10]:
1. Jornada = ParteDiario.fecha (el modelo no tiene turnos).
2. Entran los partes con fecha en el periodo y sus actividades. Los filtros se aplican antes de agregar.
3. Fila de detalle: jornada × cuadrilla × tipo de actividad × espacio.
4. Horas-persona = Σ(tiempo_minutos × participantes de la actividad) / 60, redondeado a 2 decimales.
   Las actividades sin `tiempo_minutos` no suman y se cuentan en `actividades_sin_tiempo`.
5. Apoyo eventual (CU-MOD04-011 1a, frente a RI-21): participante sin AsignacionCuadrilla vigente en la
   cuadrilla del parte en esa fecha. Se marca, no se excluye.
6. Totales por cuadrilla y jornada: cada operario cuenta una vez aunque participe en varias actividades.
7. Cuadrillas sin parte: para cada jornada con al menos un parte, cuadrillas activas sin parte → «Sin parte aún».
8. Orden: jornada, cuadrilla, clase, tipo y espacio (por nombre).

RNF-008 y RI-34: no se leen ni se devuelven `documento_identidad` ni `telefono` del Personal.
HU-MOD06-002: la consulta no modifica datos operativos; la exportación (RF-REP-009) queda fuera de este corte.
"""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import date

from .. import fuentes_datos
from ..comun.fechas import a_fecha, ahora_iso
from ..comun.periodo import Periodo
from ..fuentes_datos.base import FuenteDatos
from . import nombre_completo

MENSAJE_SIN_DATOS = "No hay partes diarios registrados en el periodo elegido"
MENSAJE_SIN_ACTIVIDADES_FILTRO = "No hay actividades registradas en el periodo elegido con los filtros aplicados"
ESTADO_SIN_PARTE = "Sin parte aún"


@dataclass(frozen=True)
class OperarioFila:
    """Operario que participó en las actividades de una fila."""

    operario_id: int
    nombre_completo: str | None
    es_apoyo: bool


@dataclass(frozen=True)
class FilaDistribucion:
    """Jornada × cuadrilla × tipo de actividad × espacio."""

    jornada: date
    cuadrilla_id: int | None
    cuadrilla_nombre: str | None
    capataz_id: int | None
    capataz_nombre: str | None
    clase_actividad_id: int | None
    clase_actividad_nombre: str | None
    tipo_actividad_id: int | None
    tipo_actividad_nombre: str | None
    area_verde_id: int | None
    area_verde_nombre: str | None
    numero_actividades: int
    operarios: tuple[OperarioFila, ...]
    numero_operarios: int
    tiempo_minutos: int | None
    horas_persona: float | None
    superficie_m2: float | None


@dataclass(frozen=True)
class TotalCuadrillaJornada:
    """Totales de una cuadrilla en una jornada."""

    jornada: date
    cuadrilla_id: int | None
    cuadrilla_nombre: str | None
    numero_actividades: int
    numero_operarios: int
    tiempo_minutos: int | None
    horas_persona: float | None
    superficie_m2: float | None


@dataclass(frozen=True)
class CuadrillaSinParte:
    """Cuadrilla activa sin parte en una jornada en la que otras sí lo tienen."""

    jornada: date
    cuadrilla_id: int
    cuadrilla_nombre: str | None
    estado: str


@dataclass(frozen=True)
class ResumenDistribucion:
    """Resumen del periodo."""

    jornadas_con_parte: int
    partes: int
    actividades: int
    operarios_distintos: int
    horas_persona: float | None
    actividades_sin_tiempo: int


@dataclass(frozen=True)
class ResultadoDistribucionPersonal:
    """Resultado de RF-REP-004."""

    periodo: dict[str, date]
    filtros_aplicados: dict
    generado_en: str
    sin_datos: bool
    mensaje_sin_datos: str | None
    filas: tuple[FilaDistribucion, ...]
    totales_por_cuadrilla_jornada: tuple[TotalCuadrillaJornada, ...]
    cuadrillas_sin_parte: tuple[CuadrillaSinParte, ...]
    resumen: ResumenDistribucion | None


def calcular(
    periodo: Periodo,
    cuadrilla_id: int | None = None,
    clase_actividad_id: int | None = None,
    tipo_actividad_id: int | None = None,
    area_verde_id: int | None = None,
    fuente: FuenteDatos | None = None,
) -> ResultadoDistribucionPersonal:
    """
    Genera la distribución del personal en `periodo` con los filtros opcionales.

    RF-REP-004 · HU-MOD06-002 · CU-MOD06-002. Solo lectura.
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    filtros_aplicados = {
        clave: valor
        for clave, valor in {
            "cuadrilla_id": cuadrilla_id,
            "clase_actividad_id": clase_actividad_id,
            "tipo_actividad_id": tipo_actividad_id,
            "area_verde_id": area_verde_id,
        }.items()
        if valor is not None
    }

    partes_periodo = [p for p in fuente.listar("ParteDiario") if periodo.contiene(a_fecha(p.get("fecha")))]
    jornadas_con_algun_parte = sorted({a_fecha(p["fecha"]) for p in partes_periodo})
    partes = [p for p in partes_periodo if cuadrilla_id is None or p.get("cuadrilla_id") == cuadrilla_id]

    cuadrillas = {c["id"]: c for c in fuente.listar("Cuadrilla")}
    cuadrillas_sin_parte = _cuadrillas_sin_parte(partes_periodo, jornadas_con_algun_parte, cuadrillas, cuadrilla_id)

    def resultado_vacio(mensaje: str) -> ResultadoDistribucionPersonal:
        return ResultadoDistribucionPersonal(
            periodo=periodo.como_dict(),
            filtros_aplicados=filtros_aplicados,
            generado_en=ahora_iso(),
            sin_datos=True,
            mensaje_sin_datos=mensaje,
            filas=(),
            totales_por_cuadrilla_jornada=(),
            cuadrillas_sin_parte=cuadrillas_sin_parte,
            resumen=None,
        )

    if not partes:
        return resultado_vacio(MENSAJE_SIN_DATOS)

    # --- Catálogos ----------------------------------------------------------
    tipos = {t["id"]: t for t in fuente.listar("TipoActividad")}
    clases = {c["id"]: c for c in fuente.listar("ClaseActividad")}
    areas = {a["id"]: a for a in fuente.listar("AreaVerde")}
    personal = {
        p["id"]: {"id": p["id"], "nombres": p.get("nombres"), "apellidos": p.get("apellidos")}
        for p in fuente.listar("Personal")
    }
    asignaciones = fuente.listar("AsignacionCuadrilla")

    # --- Actividades de los partes, con filtros -----------------------------
    partes_por_id = {p["id"]: p for p in partes}
    actividades = []
    for actividad in fuente.listar("Actividad"):
        if actividad.get("parte_id") not in partes_por_id:
            continue
        tipo = tipos.get(actividad.get("tipo_actividad_id"))
        clase_id = tipo.get("clase_id") if tipo else None
        if clase_actividad_id is not None and clase_id != clase_actividad_id:
            continue
        if tipo_actividad_id is not None and actividad.get("tipo_actividad_id") != tipo_actividad_id:
            continue
        if area_verde_id is not None and actividad.get("area_verde_id") != area_verde_id:
            continue
        actividades.append(actividad)

    if not actividades:
        return resultado_vacio(MENSAJE_SIN_ACTIVIDADES_FILTRO)

    ids_actividades = {a["id"] for a in actividades}
    participantes: dict[int, list[int]] = defaultdict(list)
    for participacion in fuente.listar("ParticipacionActividad"):
        actividad_id = participacion.get("actividad_id")
        operario_id = participacion.get("operario_id")
        if actividad_id in ids_actividades and operario_id not in participantes[actividad_id]:
            participantes[actividad_id].append(operario_id)

    # --- Agregación ---------------------------------------------------------
    grupos: dict[tuple, list[dict]] = defaultdict(list)
    for actividad in actividades:
        parte = partes_por_id[actividad["parte_id"]]
        clave = (
            a_fecha(parte["fecha"]),
            parte.get("cuadrilla_id"),
            actividad.get("tipo_actividad_id"),
            actividad.get("area_verde_id"),
        )
        grupos[clave].append(actividad)

    filas: list[FilaDistribucion] = []
    for (jornada, cuad_id, tipo_id, area_id), acts in grupos.items():
        parte = partes_por_id[acts[0]["parte_id"]]
        cuadrilla = cuadrillas.get(cuad_id)
        tipo = tipos.get(tipo_id)
        clase = clases.get(tipo.get("clase_id")) if tipo else None
        area = areas.get(area_id)
        capataz_id = parte.get("capataz_id")

        operarios_ids = _unicos(op for a in acts for op in participantes.get(a["id"], []))
        operarios = tuple(
            sorted(
                (
                    OperarioFila(
                        operario_id=op,
                        nombre_completo=nombre_completo(personal.get(op)),
                        es_apoyo=not _asignacion_vigente(asignaciones, op, cuad_id, jornada),
                    )
                    for op in operarios_ids
                ),
                key=lambda o: (o.nombre_completo or "", o.operario_id),
            )
        )
        tiempo, horas = _tiempo_y_horas(acts, participantes)
        filas.append(
            FilaDistribucion(
                jornada=jornada,
                cuadrilla_id=cuad_id,
                cuadrilla_nombre=cuadrilla.get("nombre") if cuadrilla else None,
                capataz_id=capataz_id,
                capataz_nombre=nombre_completo(personal.get(capataz_id)),
                clase_actividad_id=clase.get("id") if clase else None,
                clase_actividad_nombre=clase.get("nombre") if clase else None,
                tipo_actividad_id=tipo_id,
                tipo_actividad_nombre=tipo.get("nombre") if tipo else None,
                area_verde_id=area_id,
                area_verde_nombre=area.get("nombre") if area else None,
                numero_actividades=len(acts),
                operarios=operarios,
                numero_operarios=len(operarios),
                tiempo_minutos=tiempo,
                horas_persona=horas,
                superficie_m2=_suma_o_nulo(a.get("superficie_m2") for a in acts),
            )
        )

    filas.sort(
        key=lambda f: (
            f.jornada,
            f.cuadrilla_nombre or "",
            f.clase_actividad_nombre or "",
            f.tipo_actividad_nombre or "",
            f.area_verde_nombre or "",
        )
    )

    # --- Totales por cuadrilla y jornada ------------------------------------
    por_cuadrilla_jornada: dict[tuple, list[dict]] = defaultdict(list)
    for actividad in actividades:
        parte = partes_por_id[actividad["parte_id"]]
        por_cuadrilla_jornada[(a_fecha(parte["fecha"]), parte.get("cuadrilla_id"))].append(actividad)

    totales = []
    for (jornada, cuad_id), acts in por_cuadrilla_jornada.items():
        cuadrilla = cuadrillas.get(cuad_id)
        tiempo, horas = _tiempo_y_horas(acts, participantes)
        totales.append(
            TotalCuadrillaJornada(
                jornada=jornada,
                cuadrilla_id=cuad_id,
                cuadrilla_nombre=cuadrilla.get("nombre") if cuadrilla else None,
                numero_actividades=len(acts),
                numero_operarios=len(_unicos(op for a in acts for op in participantes.get(a["id"], []))),
                tiempo_minutos=tiempo,
                horas_persona=horas,
                superficie_m2=_suma_o_nulo(a.get("superficie_m2") for a in acts),
            )
        )
    totales.sort(key=lambda t: (t.jornada, t.cuadrilla_nombre or ""))

    # --- Resumen ------------------------------------------------------------
    _, horas_totales = _tiempo_y_horas(actividades, participantes)
    resumen = ResumenDistribucion(
        jornadas_con_parte=len({a_fecha(p["fecha"]) for p in partes}),
        partes=len(partes),
        actividades=len(actividades),
        operarios_distintos=len(_unicos(op for a in actividades for op in participantes.get(a["id"], []))),
        horas_persona=horas_totales,
        actividades_sin_tiempo=sum(1 for a in actividades if a.get("tiempo_minutos") is None),
    )

    return ResultadoDistribucionPersonal(
        periodo=periodo.como_dict(),
        filtros_aplicados=filtros_aplicados,
        generado_en=ahora_iso(),
        sin_datos=False,
        mensaje_sin_datos=None,
        filas=tuple(filas),
        totales_por_cuadrilla_jornada=tuple(totales),
        cuadrillas_sin_parte=cuadrillas_sin_parte,
        resumen=resumen,
    )


# --- Utilidades ---------------------------------------------------------------


def _unicos(valores) -> list:
    vistos: list = []
    for valor in valores:
        if valor not in vistos:
            vistos.append(valor)
    return vistos


def _asignacion_vigente(asignaciones: list[dict], operario_id: int, cuadrilla_id: int | None, fecha: date) -> bool:
    """`True` si el operario tiene una AsignacionCuadrilla vigente en esa cuadrilla en esa fecha (RI-04, RI-21)."""
    for asignacion in asignaciones:
        if asignacion.get("operario_id") != operario_id or asignacion.get("cuadrilla_id") != cuadrilla_id:
            continue
        inicio = a_fecha(asignacion.get("fecha_inicio"))
        fin = a_fecha(asignacion.get("fecha_fin"))
        if inicio is not None and inicio <= fecha and (fin is None or fin >= fecha):
            return True
    return False


def _tiempo_y_horas(actividades: list[dict], participantes: dict[int, list[int]]) -> tuple[int | None, float | None]:
    """Suma de `tiempo_minutos` y horas-persona (regla 4). Ambos nulos si ninguna actividad tiene tiempo."""
    con_tiempo = [a for a in actividades if a.get("tiempo_minutos") is not None]
    if not con_tiempo:
        return None, None
    tiempo = sum(a["tiempo_minutos"] for a in con_tiempo)
    minutos_persona = sum(a["tiempo_minutos"] * len(participantes.get(a["id"], [])) for a in con_tiempo)
    return tiempo, round(minutos_persona / 60, 2)


def _suma_o_nulo(valores) -> float | None:
    presentes = [v for v in valores if v is not None]
    return sum(presentes) if presentes else None


def _cuadrillas_sin_parte(
    partes_periodo: list[dict],
    jornadas: list[date],
    cuadrillas: dict[int, dict],
    cuadrilla_id: int | None,
) -> tuple[CuadrillaSinParte, ...]:
    """Regla 7: cuadrillas activas sin parte en jornadas donde existe al menos un parte."""
    con_parte = {(a_fecha(p["fecha"]), p.get("cuadrilla_id")) for p in partes_periodo}
    candidatas = [
        c
        for c in cuadrillas.values()
        if c.get("activa") is True and (cuadrilla_id is None or c["id"] == cuadrilla_id)
    ]
    candidatas.sort(key=lambda c: (c.get("nombre") or "", c["id"]))
    return tuple(
        CuadrillaSinParte(
            jornada=jornada,
            cuadrilla_id=c["id"],
            cuadrilla_nombre=c.get("nombre"),
            estado=ESTADO_SIN_PARTE,
        )
        for jornada in jornadas
        for c in candidatas
        if (jornada, c["id"]) not in con_parte
    )
