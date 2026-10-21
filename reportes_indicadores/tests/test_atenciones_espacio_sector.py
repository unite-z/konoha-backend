"""
Pruebas de RF-REP-006 (atenciones por espacio y sector). Caso de prueba: CP-TR-MOD06-014.

HU-MOD06-014: indicadores por espacio y sector; los espacios sin sector se conservan en un grupo identificado; el
cuartel solo se usa cuando está registrado y no sustituye al sector; sin datos se informa del estado vacío.
"""

from datetime import date

import pytest

from reportes_indicadores.comun.periodo import Periodo
from reportes_indicadores.servicios import atenciones_espacio_sector as aes

from .conftest import FuenteDatosMemoria

PERIODO = Periodo(date(2026, 9, 1), date(2026, 10, 20))


def _act(aid, area, instante, tipo=14, ejemplar=None):
    return {"id": aid, "parte_id": 1, "tipo_actividad_id": tipo, "area_verde_id": area, "ejemplar_id": ejemplar,
            "fecha_hora_actividad": instante}


def _atencion(aid, tipo, estado, inicio, fin=None):
    return {"id": aid, "codigo": f"AT-{aid}", "tipo_servicio_id": tipo, "estado": estado,
            "fecha_hora_inicio": inicio, "fecha_hora_fin": fin}


@pytest.fixture
def datos_atenciones() -> dict:
    return {
        "SectorMantenimiento": [
            {"id": 1, "nombre": "Sector 1", "atencion_a_demanda": False},
            {"id": 2, "nombre": "Sector 2", "atencion_a_demanda": False},
        ],
        "AsignacionSector": [
            {"id": 1, "sector_id": 1, "capataz_id": 2, "fecha_inicio": "2025-01-01", "fecha_fin": "2025-12-31"},
            {"id": 2, "sector_id": 1, "capataz_id": 3, "fecha_inicio": "2026-01-01", "fecha_fin": None},
        ],
        "Personal": [
            {"id": 2, "nombres": "Andrés", "apellidos": "(ejemplo)"},
            {"id": 3, "nombres": "Oscar", "apellidos": "(ejemplo)"},
        ],
        "Cuartel": [{"id": 1, "numero": 1, "nombre": "Cuartel 1", "estado": "Vigente"}],
        "AreaVerde": [
            {"id": 1, "nombre": "Tontódromo", "sector_id": 1, "cuartel_id": 1},
            {"id": 2, "nombre": "Educación", "sector_id": 1, "cuartel_id": None},
            {"id": 3, "nombre": "Biblioteca Central", "sector_id": 2, "cuartel_id": None},
            {"id": 10, "nombre": "Bosque Húmedo", "sector_id": None, "cuartel_id": None},
        ],
        "Ejemplar": [
            {"id": 1, "codigo": "EJ-00412", "area_verde_id": 10},
            {"id": 2, "codigo": "EJ-00105", "area_verde_id": 1},
            {"id": 3, "codigo": "EJ-00733", "area_verde_id": None},
        ],
        "ClaseActividad": [{"id": 3, "nombre": "Mantenimiento de jardines"}, {"id": 4, "nombre": "Poda"}],
        "TipoActividad": [{"id": 14, "clase_id": 3, "nombre": "Canteo"}, {"id": 24, "clase_id": 4, "nombre": "Poda de mantenimiento"}],
        "TipoServicio": [
            {"id": 2, "nombre": "Control fitosanitario", "ejecutor": "Tercerizado"},
            {"id": 4, "nombre": "Poda de altura a demanda", "ejecutor": "Tercerizado"},
        ],
        "ProgramacionServicio": [{"id": 9, "tipo_servicio_id": 2, "estacion": "Primavera"}],
        "Actividad": [
            _act(1, 1, "2026-09-10T08:00:00-05:00"),
            _act(2, 1, "2026-10-01T08:00:00-05:00"),
            _act(3, 2, "2026-10-02T08:00:00-05:00", tipo=24, ejemplar=2),
            _act(4, 3, "2026-10-03T08:00:00-05:00"),
            _act(5, 3, "2026-08-20T08:00:00-05:00"),  # fuera del periodo
        ],
        "AtencionTercerizada": [
            _atencion(1, 4, "Conforme / cerrado", "2026-09-15T08:00:00-05:00"),
            _atencion(2, 4, "OC referenciada", "2026-09-20T08:00:00-05:00"),  # no ejecutada
            _atencion(3, 2, "Conforme / cerrado", "2026-09-24T07:00:00-05:00"),
            _atencion(4, 4, "Observado", None, "2026-10-10T12:00:00-05:00"),  # fecha por fin
        ],
        "AlcanceAtencion": [
            {"id": 1, "atencion_id": 1, "area_verde_id": 10, "ejemplar_id": None},
            {"id": 2, "atencion_id": 1, "area_verde_id": None, "ejemplar_id": 1},  # misma área: cuenta 1
            {"id": 3, "atencion_id": 2, "area_verde_id": 3, "ejemplar_id": None},
            {"id": 4, "atencion_id": 3, "area_verde_id": 1, "ejemplar_id": None},
            {"id": 5, "atencion_id": 3, "area_verde_id": 3, "ejemplar_id": None},
            {"id": 6, "atencion_id": 4, "area_verde_id": 2, "ejemplar_id": None},
        ],
        "AplicacionFitosanitaria": [
            {"id": 1, "programacion_id": 9, "atencion_id": 3, "fecha": "2026-09-24"},  # ya contada por la atención
            {"id": 2, "programacion_id": 9, "atencion_id": None, "fecha": "2026-10-05"},
            {"id": 3, "programacion_id": None, "atencion_id": None, "fecha": "2026-10-06"},
        ],
        "AplicacionObjetivo": [
            {"id": 1, "aplicacion_id": 1, "area_verde_id": 1, "ejemplar_id": None},
            {"id": 2, "aplicacion_id": 1, "area_verde_id": 3, "ejemplar_id": None},
            {"id": 3, "aplicacion_id": 2, "area_verde_id": 2, "ejemplar_id": None},
            {"id": 4, "aplicacion_id": 2, "area_verde_id": None, "ejemplar_id": 2},
            {"id": 5, "aplicacion_id": 3, "area_verde_id": 3, "ejemplar_id": None},
        ],
        "IntervencionArbolado": [
            {"id": 1, "ejemplar_id": 2, "categoria": "Menor", "actividad_id": 3, "atencion_id": None, "fecha_ejecucion": "2026-10-02"},
            {"id": 2, "ejemplar_id": 1, "categoria": "AlturaDemanda", "actividad_id": None, "atencion_id": 1, "fecha_ejecucion": "2026-09-15"},
            {"id": 3, "ejemplar_id": 3, "categoria": "Menor", "actividad_id": None, "atencion_id": None, "fecha_ejecucion": "2026-10-02"},
            {"id": 4, "ejemplar_id": 2, "categoria": "MayorCampana", "actividad_id": None, "atencion_id": None, "fecha_ejecucion": "2026-10-06"},
            {"id": 5, "ejemplar_id": 2, "categoria": "Menor", "actividad_id": None, "atencion_id": None, "fecha_ejecucion": None},
        ],
        "DefinicionIndicador": [{"id": 2, "codigo": "RF-REP-006", "nombre": "Atenciones por espacio y sector",
                                 "descripcion_calculo": "…", "meta": None, "unidad": "atenciones"}],
    }


def _sector(resultado, sector_id):
    return next(g for g in resultado.por_sector if g.sector_id == sector_id)


def _espacio(resultado, area_id):
    return next(e for g in resultado.por_sector for e in g.espacios if e.area_verde_id == area_id)


def test_conteo_por_sector_y_espacio(datos_atenciones):
    r = aes.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_atenciones))
    assert (_espacio(r, 1).total, _espacio(r, 1).personal_propio, _espacio(r, 1).tercerizado) == (5, 2, 3)
    assert (_espacio(r, 2).total, _espacio(r, 2).personal_propio, _espacio(r, 2).tercerizado) == (3, 1, 2)
    assert (_espacio(r, 3).total, _espacio(r, 3).personal_propio, _espacio(r, 3).tercerizado) == (3, 1, 2)
    assert _sector(r, 1).total == 8 and _sector(r, 2).total == 3
    assert _espacio(r, 1).por_tipo == {
        "Control fitosanitario": 2,
        "Mantenimiento de jardines": 2,
        "Poda (MayorCampana)": 1,
    }
    assert r.resumen.total_atenciones == 13
    assert r.resumen.espacios_con_atenciones == 4
    assert r.resumen.sectores_con_atenciones == 2
    assert _sector(r, 1).capataz_vigente == {"id": 3, "nombre": "Oscar (ejemplo)"}
    assert r.indicador["codigo"] == "RF-REP-006"
    assert r.notas and "historial de área a sector" in r.notas[0]


def test_grupo_sin_sector_existe_y_va_al_final(datos_atenciones):
    for orden in ("asc", "desc"):
        r = aes.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_atenciones), orden=orden)
        ultimo = r.por_sector[-1]
        assert ultimo.sector_id is None and ultimo.sector_nombre == "Sin sector"
        assert [e.area_verde_nombre for e in ultimo.espacios] == ["Bosque Húmedo"]
        assert ultimo.total == 1  # alcance por área y por ejemplar de la misma atención: 1


def test_sin_espacio(datos_atenciones):
    r = aes.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_atenciones))
    assert r.sin_espacio.total == 1
    assert r.sin_espacio.personal_propio == 1
    assert r.sin_espacio.por_tipo == {"Poda (Menor)": 1}


def test_cuartel_solo_si_esta_registrado(datos_atenciones):
    r = aes.calcular(PERIODO, fuente=FuenteDatosMemoria(datos_atenciones))
    assert [(c.cuartel_id, c.numero, c.total) for c in r.por_cuartel] == [(1, 1, 5)]


def test_sin_doble_conteo(datos_atenciones):
    eventos = aes.recolectar_eventos(FuenteDatosMemoria(datos_atenciones), PERIODO)
    origenes = {(e.origen, e.origen_id) for e in eventos}
    # poda menor con actividad, poda de altura con atención y aplicación con atención: no cuentan por sí mismas
    assert ("IntervencionArbolado", 1) not in origenes
    assert ("IntervencionArbolado", 2) not in origenes
    assert ("AplicacionFitosanitaria", 1) not in origenes
    # alcance duplicado (área y ejemplar de la misma área): un solo par
    assert sum(1 for e in eventos if e.origen == "AtencionTercerizada" and e.origen_id == 1) == 1
    # intervención sin fecha de ejecución: no cuenta
    assert ("IntervencionArbolado", 5) not in origenes


def test_atencion_no_ejecutada_no_cuenta(datos_atenciones):
    eventos = aes.recolectar_eventos(FuenteDatosMemoria(datos_atenciones), PERIODO)
    assert not any(e.origen == "AtencionTercerizada" and e.origen_id == 2 for e in eventos)


def test_modalidad_derivada(datos_atenciones):
    eventos = {(e.origen, e.origen_id, e.area_verde_id): e for e in aes.recolectar_eventos(FuenteDatosMemoria(datos_atenciones), PERIODO)}
    assert eventos[("Actividad", 1, 1)].modalidad == "PersonalPropio"
    assert eventos[("AtencionTercerizada", 3, 1)].modalidad == "Tercerizado"
    assert eventos[("AplicacionFitosanitaria", 2, 2)].modalidad == "Tercerizado"
    assert eventos[("AplicacionFitosanitaria", 2, 2)].tipo_atencion == "Control fitosanitario"
    # sin programación: «Tercerizado» y «Control fitosanitario» (RS-002)
    assert eventos[("AplicacionFitosanitaria", 3, 3)].modalidad == "Tercerizado"
    assert eventos[("AplicacionFitosanitaria", 3, 3)].tipo_atencion == "Control fitosanitario"
    assert eventos[("IntervencionArbolado", 3, None)].modalidad == "PersonalPropio"
    assert eventos[("IntervencionArbolado", 4, 1)].modalidad == "Tercerizado"
    # fecha por fecha_hora_fin cuando no hay inicio
    assert eventos[("AtencionTercerizada", 4, 2)].fecha == date(2026, 10, 10)


def test_orden_asc_y_desc(datos_atenciones):
    fuente = FuenteDatosMemoria(datos_atenciones)
    desc = aes.calcular(PERIODO, fuente=fuente)
    assert [g.sector_id for g in desc.por_sector] == [1, 2, None]
    assert [e.area_verde_id for e in _sector(desc, 1).espacios] == [1, 2]
    asc = aes.calcular(PERIODO, fuente=fuente, orden="asc")
    assert [g.sector_id for g in asc.por_sector] == [2, 1, None]
    assert [e.area_verde_id for e in _sector(asc, 1).espacios] == [2, 1]


def test_filtros(datos_atenciones):
    fuente = FuenteDatosMemoria(datos_atenciones)
    propio = aes.calcular(PERIODO, fuente=fuente, modalidad="PersonalPropio")
    assert propio.resumen.total_atenciones == 5 and propio.resumen.tercerizado == 0

    sin_sector = aes.calcular(PERIODO, fuente=fuente, sector_id="sin_sector")
    assert [g.sector_id for g in sin_sector.por_sector] == [None]
    assert sin_sector.sin_espacio.total == 0
    assert sin_sector.filtros_aplicados == {"sector_id": "sin_sector"}

    sector_2 = aes.calcular(PERIODO, fuente=fuente, sector_id=2)
    assert [g.sector_id for g in sector_2.por_sector] == [2]

    area = aes.calcular(PERIODO, fuente=fuente, area_verde_id=2)
    assert area.resumen.total_atenciones == 3


def test_fecha_local_america_lima(datos_atenciones):
    # 2026-10-21T02:00Z = 2026-10-20 21:00 en Lima: pertenece al periodo
    datos_atenciones["Actividad"].append(_act(6, 3, "2026-10-21T02:00:00+00:00"))
    eventos = aes.recolectar_eventos(FuenteDatosMemoria(datos_atenciones), PERIODO)
    assert any(e.origen == "Actividad" and e.origen_id == 6 for e in eventos)


def test_sin_datos(datos_atenciones):
    r = aes.calcular(Periodo(date(2026, 12, 1), date(2026, 12, 31)), fuente=FuenteDatosMemoria(datos_atenciones))
    assert r.sin_datos is True
    assert r.mensaje_sin_datos == "No hay atenciones realizadas en el periodo elegido"
    assert r.por_sector == () and r.resumen is None and r.sin_espacio is None


def test_no_escribe(datos_atenciones):
    fuente = FuenteDatosMemoria(datos_atenciones)
    aes.calcular(PERIODO, fuente=fuente)
    assert fuente.escrituras == []
