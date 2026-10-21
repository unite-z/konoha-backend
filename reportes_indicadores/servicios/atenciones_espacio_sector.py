"""
Servicio de RF-REP-006: atenciones por espacio y sector (agrupador principal: sector).

Trazabilidad: RF-REP-006 · HU-MOD06-014 · CU-MOD06-014 · CP-TR-MOD06-014 · CU-MOD06-001 (actualización v2:
el sector es el agrupador principal; el cuartel, solo si está registrado) · RS-002, RN-005.

Texto del requisito (Catálogo v2): «El sistema deberá mostrar el número de atenciones realizadas por espacio y
por sector (y por cuartel cuando esté registrado) en un periodo, para ayudar a priorizar espacios».

Fuentes de «atención» y regla anti doble conteo [Definición acordada 20/10]. Cada evento cuenta una sola vez:

| Origen                     | Cuenta si                                         | Fecha                          | Espacio                                   | Modalidad                          | Tipo                         |
|----------------------------|---------------------------------------------------|--------------------------------|-------------------------------------------|------------------------------------|------------------------------|
| Actividad (MOD-04)         | siempre                                           | fecha local de fecha_hora_actividad | area_verde_id                        | PersonalPropio                     | ClaseActividad.nombre        |
| AtencionTercerizada        | estado ejecutado (ver ESTADOS_ATENCION_EJECUTADA) | fecha local de inicio (o de fin) | AlcanceAtencion.area_verde_id o Ejemplar | Tercerizado                        | TipoServicio.nombre          |
| AplicacionFitosanitaria    | atencion_id nulo                                  | fecha                          | AplicacionObjetivo.area_verde_id o Ejemplar | TipoServicio.ejecutor o Tercerizado (RS-002) | TipoServicio.nombre o «Control fitosanitario» |
| IntervencionArbolado       | actividad_id y atencion_id nulos, fecha_ejecucion | fecha_ejecucion                | Ejemplar.area_verde_id                    | Menor → PersonalPropio; otras → Tercerizado (RN-005) | «Poda (<categoria>)» |

Cada par (evento, área) cuenta 1: una atención con varias áreas suma 1 en cada área, y dos filas de alcance de
la misma atención que apuntan a la misma área cuentan 1.

Agrupación: sector (AreaVerde.sector_id; «Sin sector» si es nulo, HU-MOD06-014 y RF-INV-015) → espacio.
Los eventos cuyo espacio no se puede resolver van aparte en `sin_espacio` y no se reparten entre sectores.
El cuartel es secundario: solo áreas con `cuartel_id` registrado; no sustituye al sector.

Limitación conocida: la agrupación usa el sector vigente del área; el modelo de datos no guarda el historial de
área a sector (Modelo de BD v1, decisión 8).
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date

from .. import fuentes_datos
from ..comun.fechas import a_fecha, a_fecha_hora, ahora_iso, fecha_local
from ..comun.periodo import Periodo
from ..fuentes_datos.base import FuenteDatos
from . import indicador_por_codigo, nombre_completo

CODIGO_INDICADOR = "RF-REP-006"
MENSAJE_SIN_DATOS = "No hay atenciones realizadas en el periodo elegido"
NOTA_LIMITACION_SECTOR = (
    "La agrupación usa el sector vigente del área; el modelo de datos no guarda el historial de área a sector "
    "(Modelo de BD v1, decisión 8)"
)

PERSONAL_PROPIO = "PersonalPropio"
TERCERIZADO = "Tercerizado"
MODALIDADES = (PERSONAL_PROPIO, TERCERIZADO)
SIN_SECTOR = "sin_sector"
NOMBRE_GRUPO_SIN_SECTOR = "Sin sector"
ORDEN_ASC = "asc"
ORDEN_DESC = "desc"

ESTADOS_ATENCION_EJECUTADA = frozenset(
    {"Ejecutado pendiente de reporte", "Reporte adjunto", "Observado", "Conforme / cerrado"}
)
TIPO_FITOSANITARIO_POR_DEFECTO = "Control fitosanitario"
MODALIDAD_PODA = {"Menor": PERSONAL_PROPIO, "MayorCampana": TERCERIZADO, "AlturaDemanda": TERCERIZADO}


@dataclass(frozen=True)
class Evento:
    """Un par (evento, área) que cuenta como una atención."""

    origen: str
    origen_id: int
    fecha: date
    area_verde_id: int | None
    modalidad: str
    tipo_atencion: str


@dataclass(frozen=True)
class Medidas:
    """Medidas de un grupo."""

    total: int
    personal_propio: int
    tercerizado: int
    por_tipo: dict[str, int]


@dataclass(frozen=True)
class GrupoEspacio:
    area_verde_id: int
    area_verde_nombre: str | None
    total: int
    personal_propio: int
    tercerizado: int
    por_tipo: dict[str, int]


@dataclass(frozen=True)
class GrupoSector:
    sector_id: int | None
    sector_nombre: str | None
    atencion_a_demanda: bool | None
    capataz_vigente: dict | None
    total: int
    personal_propio: int
    tercerizado: int
    por_tipo: dict[str, int]
    espacios: tuple[GrupoEspacio, ...]


@dataclass(frozen=True)
class GrupoCuartel:
    cuartel_id: int
    numero: int | None
    nombre: str | None
    total: int
    personal_propio: int
    tercerizado: int


@dataclass(frozen=True)
class ResumenAtenciones:
    total_atenciones: int
    personal_propio: int
    tercerizado: int
    espacios_con_atenciones: int
    sectores_con_atenciones: int


@dataclass(frozen=True)
class ResultadoAtencionesEspacioSector:
    """Resultado de RF-REP-006."""

    periodo: dict[str, date]
    filtros_aplicados: dict
    generado_en: str
    sin_datos: bool
    mensaje_sin_datos: str | None
    indicador: dict | None
    por_sector: tuple[GrupoSector, ...]
    sin_espacio: Medidas | None
    por_cuartel: tuple[GrupoCuartel, ...]
    resumen: ResumenAtenciones | None
    notas: tuple[str, ...]


def calcular(
    periodo: Periodo,
    fuente: FuenteDatos | None = None,
    **filtros: object,
) -> ResultadoAtencionesEspacioSector:
    """
    Cuenta las atenciones realizadas en `periodo` por sector, espacio y cuartel.

    Filtros opcionales: `sector_id` (entero o "sin_sector"), `modalidad` ("PersonalPropio" | "Tercerizado"),
    `area_verde_id` (entero) y `orden` ("asc" | "desc"; por defecto "desc").
    RF-REP-006 · HU-MOD06-014 · CP-TR-MOD06-014. Solo lectura.
    """
    fuente = fuente or fuentes_datos.obtener_fuente_datos()
    sector_id = filtros.get("sector_id")
    modalidad = filtros.get("modalidad")
    area_verde_id = filtros.get("area_verde_id")
    orden = filtros.get("orden") or ORDEN_DESC
    filtros_aplicados = {k: v for k, v in filtros.items() if v is not None}

    indicador = indicador_por_codigo(fuente, CODIGO_INDICADOR)
    areas = {a["id"]: a for a in fuente.listar("AreaVerde")}

    eventos = [e for e in recolectar_eventos(fuente, periodo) if _pasa_filtros(e, areas, sector_id, modalidad, area_verde_id)]

    if not eventos:
        return ResultadoAtencionesEspacioSector(
            periodo=periodo.como_dict(),
            filtros_aplicados=filtros_aplicados,
            generado_en=ahora_iso(),
            sin_datos=True,
            mensaje_sin_datos=MENSAJE_SIN_DATOS,
            indicador=indicador,
            por_sector=(),
            sin_espacio=None,
            por_cuartel=(),
            resumen=None,
            notas=(NOTA_LIMITACION_SECTOR,),
        )

    sectores = {s["id"]: s for s in fuente.listar("SectorMantenimiento")}
    cuarteles = {c["id"]: c for c in fuente.listar("Cuartel")}
    personal = {p["id"]: p for p in fuente.listar("Personal")}
    asignaciones_sector = fuente.listar("AsignacionSector")

    con_espacio = [e for e in eventos if e.area_verde_id in areas]
    sin_espacio = [e for e in eventos if e.area_verde_id not in areas]

    # --- Sector → espacio ---------------------------------------------------
    por_sector_eventos: dict[int | None, list[Evento]] = {}
    for evento in con_espacio:
        por_sector_eventos.setdefault(areas[evento.area_verde_id].get("sector_id"), []).append(evento)

    grupos_sector: list[GrupoSector] = []
    for sid, eventos_sector in por_sector_eventos.items():
        por_area: dict[int, list[Evento]] = {}
        for evento in eventos_sector:
            por_area.setdefault(evento.area_verde_id, []).append(evento)
        espacios = [
            GrupoEspacio(area_verde_id=aid, area_verde_nombre=areas[aid].get("nombre"), **_medidas_dict(evs))
            for aid, evs in por_area.items()
        ]
        espacios.sort(key=lambda g: _clave_orden(g.total, g.area_verde_nombre, orden))
        sector = sectores.get(sid) if sid is not None else None
        if sid is None:
            sector_nombre = NOMBRE_GRUPO_SIN_SECTOR
        else:
            sector_nombre = sector.get("nombre") if sector else None
        grupos_sector.append(
            GrupoSector(
                sector_id=sid,
                sector_nombre=sector_nombre,
                atencion_a_demanda=sector.get("atencion_a_demanda") if sector else None,
                capataz_vigente=_capataz_vigente(sid, asignaciones_sector, personal),
                espacios=tuple(espacios),
                **_medidas_dict(eventos_sector),
            )
        )
    con_sector = [g for g in grupos_sector if g.sector_id is not None]
    con_sector.sort(key=lambda g: _clave_orden(g.total, g.sector_nombre, orden))
    grupos_sin_sector = [g for g in grupos_sector if g.sector_id is None]

    # --- Cuartel (secundario) -----------------------------------------------
    por_cuartel_eventos: dict[int, list[Evento]] = {}
    for evento in con_espacio:
        cuartel_id = areas[evento.area_verde_id].get("cuartel_id")
        if cuartel_id is not None:
            por_cuartel_eventos.setdefault(cuartel_id, []).append(evento)
    grupos_cuartel = []
    for cid, evs in por_cuartel_eventos.items():
        cuartel = cuarteles.get(cid) or {}
        medidas = _medidas(evs)
        grupos_cuartel.append(
            GrupoCuartel(
                cuartel_id=cid,
                numero=cuartel.get("numero"),
                nombre=cuartel.get("nombre"),
                total=medidas.total,
                personal_propio=medidas.personal_propio,
                tercerizado=medidas.tercerizado,
            )
        )
    grupos_cuartel.sort(key=lambda g: (g.numero if g.numero is not None else 0, g.cuartel_id))

    total = _medidas(eventos)
    resumen = ResumenAtenciones(
        total_atenciones=total.total,
        personal_propio=total.personal_propio,
        tercerizado=total.tercerizado,
        espacios_con_atenciones=len({e.area_verde_id for e in con_espacio}),
        sectores_con_atenciones=len(con_sector),
    )

    return ResultadoAtencionesEspacioSector(
        periodo=periodo.como_dict(),
        filtros_aplicados=filtros_aplicados,
        generado_en=ahora_iso(),
        sin_datos=False,
        mensaje_sin_datos=None,
        indicador=indicador,
        por_sector=tuple(con_sector + grupos_sin_sector),
        sin_espacio=_medidas(sin_espacio),
        por_cuartel=tuple(grupos_cuartel),
        resumen=resumen,
        notas=(NOTA_LIMITACION_SECTOR,),
    )


# --- Recolección de eventos (regla anti doble conteo) -------------------------


def recolectar_eventos(fuente: FuenteDatos, periodo: Periodo) -> list[Evento]:
    """Pares (evento, área) del periodo, cada uno una sola vez (sección 8.1 del superprompt)."""
    ejemplares = {e["id"]: e for e in fuente.listar("Ejemplar")}
    tipos_servicio = {t["id"]: t for t in fuente.listar("TipoServicio")}
    programaciones = {p["id"]: p for p in fuente.listar("ProgramacionServicio")}
    tipos_actividad = {t["id"]: t for t in fuente.listar("TipoActividad")}
    clases = {c["id"]: c for c in fuente.listar("ClaseActividad")}

    def area_de(area_verde_id: int | None, ejemplar_id: int | None) -> int | None:
        if area_verde_id is not None:
            return area_verde_id
        ejemplar = ejemplares.get(ejemplar_id)
        return ejemplar.get("area_verde_id") if ejemplar else None

    eventos: dict[tuple[str, int, int | None], Evento] = {}

    def agregar(origen: str, origen_id: int, fecha: date | None, areas_evento: list[int | None], modalidad: str, tipo: str) -> None:
        if not periodo.contiene(fecha):
            return
        for area_id in areas_evento or [None]:
            clave = (origen, origen_id, area_id)
            if clave not in eventos:
                eventos[clave] = Evento(origen, origen_id, fecha, area_id, modalidad, tipo)

    # 1. Actividad (personal propio, MOD-04)
    for actividad in fuente.listar("Actividad"):
        instante = a_fecha_hora(actividad.get("fecha_hora_actividad"))
        tipo = tipos_actividad.get(actividad.get("tipo_actividad_id"))
        clase = clases.get(tipo.get("clase_id")) if tipo else None
        agregar(
            "Actividad",
            actividad["id"],
            fecha_local(instante) if instante else None,
            [actividad.get("area_verde_id")],
            PERSONAL_PROPIO,
            (clase.get("nombre") if clase else None) or "Sin clase de actividad",
        )

    # 2. AtencionTercerizada + AlcanceAtencion
    alcances: dict[int, list[dict]] = {}
    for alcance in fuente.listar("AlcanceAtencion"):
        alcances.setdefault(alcance.get("atencion_id"), []).append(alcance)
    for atencion in fuente.listar("AtencionTercerizada"):
        if atencion.get("estado") not in ESTADOS_ATENCION_EJECUTADA:
            continue
        instante = a_fecha_hora(atencion.get("fecha_hora_inicio")) or a_fecha_hora(atencion.get("fecha_hora_fin"))
        if instante is None:
            continue
        tipo = tipos_servicio.get(atencion.get("tipo_servicio_id"))
        agregar(
            "AtencionTercerizada",
            atencion["id"],
            fecha_local(instante),
            [area_de(a.get("area_verde_id"), a.get("ejemplar_id")) for a in alcances.get(atencion["id"], [])],
            TERCERIZADO,
            (tipo.get("nombre") if tipo else None) or "Sin tipo de servicio",
        )

    # 3. AplicacionFitosanitaria sin atención + AplicacionObjetivo
    objetivos: dict[int, list[dict]] = {}
    for objetivo in fuente.listar("AplicacionObjetivo"):
        objetivos.setdefault(objetivo.get("aplicacion_id"), []).append(objetivo)
    for aplicacion in fuente.listar("AplicacionFitosanitaria"):
        if aplicacion.get("atencion_id") is not None:
            continue  # ya se contó por su atención
        programacion = programaciones.get(aplicacion.get("programacion_id"))
        tipo = tipos_servicio.get(programacion.get("tipo_servicio_id")) if programacion else None
        agregar(
            "AplicacionFitosanitaria",
            aplicacion["id"],
            a_fecha(aplicacion.get("fecha")),
            [area_de(o.get("area_verde_id"), o.get("ejemplar_id")) for o in objetivos.get(aplicacion["id"], [])],
            (tipo.get("ejecutor") if tipo else None) or TERCERIZADO,
            (tipo.get("nombre") if tipo else None) or TIPO_FITOSANITARIO_POR_DEFECTO,
        )

    # 4. IntervencionArbolado no contada por su origen
    for intervencion in fuente.listar("IntervencionArbolado"):
        if intervencion.get("actividad_id") is not None or intervencion.get("atencion_id") is not None:
            continue
        fecha = a_fecha(intervencion.get("fecha_ejecucion"))
        if fecha is None:
            continue
        categoria = intervencion.get("categoria")
        if categoria not in MODALIDAD_PODA:
            continue
        agregar(
            "IntervencionArbolado",
            intervencion["id"],
            fecha,
            [area_de(None, intervencion.get("ejemplar_id"))],
            MODALIDAD_PODA[categoria],
            f"Poda ({categoria})",
        )

    return list(eventos.values())


# --- Utilidades ---------------------------------------------------------------


def _pasa_filtros(evento: Evento, areas: dict[int, dict], sector_id, modalidad, area_verde_id) -> bool:
    if modalidad is not None and evento.modalidad != modalidad:
        return False
    if area_verde_id is not None and evento.area_verde_id != area_verde_id:
        return False
    if sector_id is not None:
        area = areas.get(evento.area_verde_id)
        if area is None:
            return False  # sin espacio: no pertenece a ningún sector
        if sector_id == SIN_SECTOR:
            return area.get("sector_id") is None
        return area.get("sector_id") == sector_id
    return True


def _medidas(eventos: list[Evento]) -> Medidas:
    return Medidas(**_medidas_dict(eventos))


def _medidas_dict(eventos: list[Evento]) -> dict:
    por_tipo = Counter(e.tipo_atencion for e in eventos)
    return {
        "total": len(eventos),
        "personal_propio": sum(1 for e in eventos if e.modalidad == PERSONAL_PROPIO),
        "tercerizado": sum(1 for e in eventos if e.modalidad == TERCERIZADO),
        "por_tipo": dict(sorted(por_tipo.items())),
    }


def _clave_orden(total: int, nombre: str | None, orden: str) -> tuple:
    """Orden por total (asc o desc) y, en empate, por nombre ascendente."""
    return (total if orden == ORDEN_ASC else -total, nombre or "")


def _capataz_vigente(sector_id: int | None, asignaciones: list[dict], personal: dict[int, dict]) -> dict | None:
    """AsignacionSector con fecha_fin nula (RI-06: como máximo una). Si hubiera varias, la más reciente."""
    if sector_id is None:
        return None
    vigentes = [a for a in asignaciones if a.get("sector_id") == sector_id and a.get("fecha_fin") is None]
    if not vigentes:
        return None
    vigente = max(vigentes, key=lambda a: (a_fecha(a.get("fecha_inicio")) or date.min, a.get("id") or 0))
    capataz_id = vigente.get("capataz_id")
    return {"id": capataz_id, "nombre": nombre_completo(personal.get(capataz_id))}
